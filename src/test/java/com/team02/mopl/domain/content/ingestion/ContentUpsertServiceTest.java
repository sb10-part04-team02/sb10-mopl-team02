package com.team02.mopl.domain.content.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.team02.mopl.domain.content.entity.Content;
import com.team02.mopl.domain.content.entity.Tag;
import com.team02.mopl.domain.content.enums.ContentSource;
import com.team02.mopl.domain.content.enums.ContentType;
import com.team02.mopl.domain.content.repository.ContentRepository;
import com.team02.mopl.domain.content.repository.TagRepository;
import com.team02.mopl.support.RepositoryTestSupport;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

@Import(ContentUpsertService.class)
class ContentUpsertServiceTest extends RepositoryTestSupport {

  @Autowired private ContentUpsertService contentUpsertService;

  @Autowired private ContentRepository contentRepository;

  @Autowired private TagRepository tagRepository;

  @Autowired private EntityManager em;

  private static ExternalContentData tmdbData(String externalId, List<String> tags) {
    return new ExternalContentData(
        ContentSource.TMDB, externalId, ContentType.MOVIE, "외부 영화", "설명", "http://img", tags);
  }

  @Test
  @DisplayName("upsert는 존재하지 않는 (source, externalId)면 콘텐츠와 태그를 새로 저장한다")
  void upsert_whenNotExists_insertsContentWithTags() {
    // given - 존재하지 않는 (source, externalId)
    // when
    UpsertResult result = contentUpsertService.upsert(tmdbData("100", List.of("액션", "코미디")));

    // then
    assertThat(result).isEqualTo(UpsertResult.INSERTED); // INSERTED
    Content saved =
        contentRepository.findBySourceAndExternalId(ContentSource.TMDB, "100").orElseThrow();
    assertThat(saved.getContentType()).isEqualTo(ContentType.MOVIE);
    assertThat(saved.getTitle()).isEqualTo("외부 영화");
    assertThat(tagRepository.findByContentIdAndDeletedAtIsNull(saved.getId()))
        .extracting(Tag::getName)
        .containsExactlyInAnyOrder("액션", "코미디");
  }

  @Test
  @DisplayName("upsert는 동일 데이터로 재실행해도 행이 늘어나지 않는다 (멱등성)")
  void upsert_whenRerunWithSameData_isIdempotent() {
    // given
    ExternalContentData data = tmdbData("100", List.of("액션"));
    contentUpsertService.upsert(data);

    // when
    UpsertResult second = contentUpsertService.upsert(data);

    // then
    assertThat(second).isEqualTo(UpsertResult.UPDATED);
    assertThat(contentRepository.count()).isEqualTo(1);
    Content content =
        contentRepository.findBySourceAndExternalId(ContentSource.TMDB, "100").orElseThrow();
    assertThat(tagRepository.findByContentIdAndDeletedAtIsNull(content.getId())).hasSize(1);
  }

  @Test
  @DisplayName("upsert는 기존 콘텐츠의 제목/설명/썸네일을 갱신하되 리뷰 집계 값은 건드리지 않는다")
  void upsert_whenExists_updatesFieldsButPreservesRatingAggregate() {
    // given
    contentUpsertService.upsert(tmdbData("100", List.of()));
    Content content =
        contentRepository.findBySourceAndExternalId(ContentSource.TMDB, "100").orElseThrow();
    em.createNativeQuery(
            "UPDATE contents SET average_rating = 4.5, review_count = 2 WHERE id = :id")
        .setParameter("id", content.getId())
        .executeUpdate();
    em.clear();

    // when
    UpsertResult result =
        contentUpsertService.upsert(
            new ExternalContentData(
                ContentSource.TMDB,
                "100",
                ContentType.MOVIE,
                "새 제목",
                "새 설명",
                "http://new-img",
                List.of()));
    em.flush();
    em.clear();

    // then
    assertThat(result).isEqualTo(UpsertResult.UPDATED);
    Content updated =
        contentRepository.findBySourceAndExternalId(ContentSource.TMDB, "100").orElseThrow();
    assertThat(updated.getTitle()).isEqualTo("새 제목");
    assertThat(updated.getDescription()).isEqualTo("새 설명");
    assertThat(updated.getThumbnailUrl()).isEqualTo("http://new-img");
    assertThat(updated.getAverageRating()).isEqualTo(4.5);
    assertThat(updated.getReviewCount()).isEqualTo(2);
  }

  @Test
  @DisplayName("upsert는 빈 제목/설명/썸네일이 오면 기존 값을 덮지 않는다 (부분 갱신)")
  void upsert_whenBlankFields_keepsExistingValues() {
    // given
    contentUpsertService.upsert(tmdbData("100", List.of()));

    // when
    UpsertResult result =
        contentUpsertService.upsert(
            new ExternalContentData(
                ContentSource.TMDB, "100", ContentType.MOVIE, "", "", "", List.of()));
    em.flush();
    em.clear();

    // then
    assertThat(result).isEqualTo(UpsertResult.UPDATED);
    Content content =
        contentRepository.findBySourceAndExternalId(ContentSource.TMDB, "100").orElseThrow();
    assertThat(content.getTitle()).isEqualTo("외부 영화");
    assertThat(content.getDescription()).isEqualTo("설명");
    assertThat(content.getThumbnailUrl()).isEqualTo("http://img");
  }

  @Test
  @DisplayName("upsert는 기존 태그를 지우지 않고 새 태그만 추가한다 (mergeAddTags)")
  void upsert_mergesNewTagsWithoutRemovingExisting() {
    // given
    contentUpsertService.upsert(tmdbData("100", List.of("액션")));

    // when
    contentUpsertService.upsert(tmdbData("100", List.of("코미디")));

    // then
    Content content =
        contentRepository.findBySourceAndExternalId(ContentSource.TMDB, "100").orElseThrow();
    assertThat(tagRepository.findByContentIdAndDeletedAtIsNull(content.getId()))
        .extracting(Tag::getName)
        .containsExactlyInAnyOrder("액션", "코미디");
  }

  @Test
  @DisplayName("upsert는 소프트 삭제된 콘텐츠를 되살리지 않고 건너뛴다")
  void upsert_whenSoftDeleted_skipsWithoutRestoring() {
    // given
    contentUpsertService.upsert(tmdbData("100", List.of()));
    Content content =
        contentRepository.findBySourceAndExternalId(ContentSource.TMDB, "100").orElseThrow();
    content.delete();
    em.flush();

    // when
    UpsertResult result = contentUpsertService.upsert(tmdbData("100", List.of()));

    // then
    assertThat(result).isEqualTo(UpsertResult.SKIPPED);
    Content after =
        contentRepository.findBySourceAndExternalId(ContentSource.TMDB, "100").orElseThrow();
    assertThat(after.isDeleted()).isTrue();
  }
}
