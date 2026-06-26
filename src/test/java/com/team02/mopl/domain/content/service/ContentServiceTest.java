package com.team02.mopl.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.team02.mopl.domain.content.dto.ContentCreateRequest;
import com.team02.mopl.domain.content.dto.ContentDto;
import com.team02.mopl.domain.content.dto.ContentUpdateRequest;
import com.team02.mopl.domain.content.entity.Content;
import com.team02.mopl.domain.content.entity.Tag;
import com.team02.mopl.domain.content.enums.ContentType;
import com.team02.mopl.domain.content.mapper.ContentMapper;
import com.team02.mopl.domain.content.repository.ContentRepository;
import com.team02.mopl.domain.content.repository.TagRepository;
import com.team02.mopl.global.exception.BusinessException;
import com.team02.mopl.global.exception.ErrorCode;
import com.team02.mopl.global.storage.FileStorage;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
@DisplayName("ContentService 단위 테스트")
class ContentServiceTest {

  @Mock ContentRepository contentRepository;

  @Mock TagRepository tagRepository;

  @Mock WatcherCountService watcherCountService;

  @Mock ContentMapper contentMapper;

  @Mock FileStorage fileStorage;

  @InjectMocks ContentService contentService;

  @Captor ArgumentCaptor<Content> contentCaptor;

  @Captor ArgumentCaptor<List<Tag>> tagListCaptor;

  // 설정값(app.storage.default-thumbnail-url)이 그대로 쓰이는지 검증하기 위한 상수
  private static final String DEFAULT_THUMBNAIL_URL = "default-thumbnail-sentinel";

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(contentService, "defaultThumbnailUrl", DEFAULT_THUMBNAIL_URL);
  }

  // id가 없는(미영속) 콘텐츠
  private Content newContent() {
    return new Content(ContentType.MOVIE, "인셉션", "꿈 속의 꿈", "https://cdn/thumb.jpg");
  }

  // id가 부여된(영속) 콘텐츠
  private Content contentWithId(UUID id) {
    Content content = new Content(ContentType.MOVIE, "구제목", "구설명", "old-url");
    ReflectionTestUtils.setField(content, "id", id);
    return content;
  }

  @Nested
  @DisplayName("get - 콘텐츠 단건 조회")
  class Get {

    @Test
    @DisplayName("존재하는 콘텐츠를 조회하면 태그와 watcherCount가 포함된 ContentDto를 반환한다")
    void success_returnsContentDto_whenContentExists() {
      // given
      UUID contentId = UUID.randomUUID();
      Content content = newContent();
      List<Tag> tags = List.of(new Tag(content, "SF"), new Tag(content, "스릴러"));
      long watcherCount = 7L;
      ContentDto expected =
          new ContentDto(
              contentId,
              ContentType.MOVIE,
              "인셉션",
              "꿈 속의 꿈",
              "https://cdn/thumb.jpg",
              List.of("SF", "스릴러"),
              4.5,
              10,
              watcherCount);

      given(contentRepository.findByIdAndDeletedAtIsNull(contentId))
          .willReturn(Optional.of(content));
      given(tagRepository.findByContentIdAndDeletedAtIsNull(contentId)).willReturn(tags);
      given(watcherCountService.count(contentId)).willReturn(watcherCount);
      given(contentMapper.toDto(content, tags, watcherCount)).willReturn(expected);

      // when
      ContentDto actual = contentService.get(contentId);

      // then
      assertThat(actual).isEqualTo(expected);
      then(contentRepository).should().findByIdAndDeletedAtIsNull(contentId);
      then(tagRepository).should().findByContentIdAndDeletedAtIsNull(contentId);
      then(watcherCountService).should().count(contentId);
      then(contentMapper).should().toDto(content, tags, watcherCount);
    }

    @Test
    @DisplayName("태그가 없는 콘텐츠도 빈 태그 목록과 watcherCount 0으로 조회된다")
    void success_returnsContentDto_whenNoTagsAndNoWatchers() {
      // given
      UUID contentId = UUID.randomUUID();
      Content content = newContent();
      ContentDto expected =
          new ContentDto(
              contentId,
              ContentType.MOVIE,
              "인셉션",
              "꿈 속의 꿈",
              "https://cdn/thumb.jpg",
              List.of(), // No Tags
              0.0,
              0,
              0L); // No Watchers

      given(contentRepository.findByIdAndDeletedAtIsNull(contentId))
          .willReturn(Optional.of(content));
      given(tagRepository.findByContentIdAndDeletedAtIsNull(contentId)).willReturn(List.of());
      given(watcherCountService.count(contentId)).willReturn(0L);
      given(contentMapper.toDto(eq(content), anyList(), eq(0L))).willReturn(expected);

      // when
      ContentDto actual = contentService.get(contentId);

      // then
      assertThat(actual).isEqualTo(expected);
      assertThat(actual.tags()).isEmpty();
      assertThat(actual.watcherCount()).isZero();
    }

    @Test
    @DisplayName("존재하지 않거나 삭제된 콘텐츠를 조회하면 CONTENT_NOT_FOUND 예외를 던진다")
    void fail_throwsContentNotFound_whenContentMissing() {
      // given
      UUID contentId = UUID.randomUUID();
      given(contentRepository.findByIdAndDeletedAtIsNull(contentId)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> contentService.get(contentId))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.CONTENT_NOT_FOUND);

      // 콘텐츠가 없으면 태그/시청자수 조회와 매핑은 수행하지 않는다
      then(tagRepository).should(never()).findByContentIdAndDeletedAtIsNull(any());
      then(watcherCountService).should(never()).count(any());
      then(contentMapper).should(never()).toDto(any(), anyList(), anyLong());
    }
  }

  @Nested
  @DisplayName("create - [어드민] 콘텐츠 생성")
  class Create {

    @Test
    @DisplayName("썸네일 저장 후 콘텐츠와 태그를 저장하고 watcherCount 0인 ContentDto를 반환한다")
    void success_savesContentAndTags_andReturnsDto() {
      // given
      ContentCreateRequest request =
          new ContentCreateRequest(ContentType.MOVIE, "인셉션", "꿈 속의 꿈", List.of("SF", "스릴러"));
      MultipartFile thumbnail = mockThumbnail(false);
      ContentDto expected =
          new ContentDto(
              UUID.randomUUID(),
              ContentType.MOVIE,
              "인셉션",
              "꿈 속의 꿈",
              "https://cdn/thumb.jpg",
              List.of("SF", "스릴러"),
              0.0,
              0,
              0L);

      given(fileStorage.store(thumbnail)).willReturn("https://cdn/thumb.jpg");
      // 신규 콘텐츠라 기존 활성 태그는 없음
      given(tagRepository.findByContentIdAndDeletedAtIsNull(any())).willReturn(List.of());
      // saveAll에 들어온 Tag 목록을 그대로 반환하도록 모킹
      given(tagRepository.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));
      given(contentMapper.toDto(any(Content.class), anyList(), eq(0L))).willReturn(expected);

      // when
      ContentDto actual = contentService.create(request, thumbnail);

      // then
      assertThat(actual).isEqualTo(expected);

      then(fileStorage).should().store(thumbnail);
      then(contentRepository).should().save(contentCaptor.capture());
      Content saved = contentCaptor.getValue();
      assertThat(saved.getContentType()).isEqualTo(ContentType.MOVIE);
      assertThat(saved.getTitle()).isEqualTo("인셉션");
      assertThat(saved.getDescription()).isEqualTo("꿈 속의 꿈");
      assertThat(saved.getThumbnailUrl()).isEqualTo("https://cdn/thumb.jpg");

      then(tagRepository).should().saveAll(tagListCaptor.capture());
      assertThat(tagListCaptor.getValue()).extracting(Tag::getName).containsExactly("SF", "스릴러");
      then(contentMapper).should().toDto(any(Content.class), anyList(), eq(0L));
    }

    @Test
    @DisplayName("태그는 공백 제거 + 빈/널 제거 + 순서 유지 중복 제거 후 저장된다")
    void success_normalizesTags_dedupAndTrim() {
      // given
      ContentCreateRequest request =
          new ContentCreateRequest(
              ContentType.MOVIE,
              "인셉션",
              "꿈 속의 꿈",
              Arrays.asList("SF", " SF ", "  ", "", null, "스릴러"));
      MultipartFile thumbnail = mockThumbnail(false);
      given(tagRepository.findByContentIdAndDeletedAtIsNull(any())).willReturn(List.of());
      given(tagRepository.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));

      // when
      contentService.create(request, thumbnail);

      // then: "SF"(중복/공백 정규화), "스릴러"만 남아 2건 저장
      then(tagRepository).should().saveAll(tagListCaptor.capture());
      assertThat(tagListCaptor.getValue()).extracting(Tag::getName).containsExactly("SF", "스릴러");
    }

    @Test
    @DisplayName("이미 존재하는 태그명은 건너뛰고 신규 태그만 저장한다")
    void success_skipsExistingTag() {
      // given
      ContentCreateRequest request =
          new ContentCreateRequest(ContentType.MOVIE, "인셉션", "꿈 속의 꿈", List.of("SF", "스릴러"));
      MultipartFile thumbnail = mockThumbnail(false);
      // 기존 활성 태그로 "SF"가 이미 존재
      given(tagRepository.findByContentIdAndDeletedAtIsNull(any()))
          .willReturn(List.of(new Tag(newContent(), "SF")));
      given(tagRepository.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));

      // when
      contentService.create(request, thumbnail);

      // then: "SF"는 이미 존재하므로 건너뛰고 "스릴러"만 저장
      then(tagRepository).should().saveAll(tagListCaptor.capture());
      assertThat(tagListCaptor.getValue()).extracting(Tag::getName).containsExactly("스릴러");
    }

    @Test
    @DisplayName("태그 목록이 비어 있으면 태그를 저장하지 않는다")
    void success_noTagSave_whenTagsEmpty() {
      // given
      ContentCreateRequest request =
          new ContentCreateRequest(ContentType.MOVIE, "인셉션", "꿈 속의 꿈", List.of());
      MultipartFile thumbnail = mockThumbnail(false);

      // when
      contentService.create(request, thumbnail);

      // then
      then(tagRepository).should(never()).saveAll(any());
      then(tagRepository).should(never()).findByContentIdAndDeletedAtIsNull(any());
      then(contentMapper).should().toDto(any(Content.class), anyList(), eq(0L));
    }

    @Test
    @DisplayName("썸네일이 null이면 fileStorage를 호출하지 않고 기본 썸네일 URL로 콘텐츠를 저장한다")
    void success_usesDefaultThumbnail_whenThumbnailIsNull() {
      // given
      ContentCreateRequest request =
          new ContentCreateRequest(ContentType.MOVIE, "인셉션", "꿈 속의 꿈", List.of());
      given(contentMapper.toDto(any(Content.class), anyList(), eq(0L)))
          .willReturn(mockDto(UUID.randomUUID(), List.of(), 0L));

      // when
      contentService.create(request, null);

      // then
      then(fileStorage).should(never()).store(any());
      then(contentRepository).should().save(contentCaptor.capture());
      assertThat(contentCaptor.getValue().getThumbnailUrl()).isEqualTo(DEFAULT_THUMBNAIL_URL);
    }
  }

  @Nested
  @DisplayName("update - 콘텐츠 수정")
  class Update {

    @Test
    @DisplayName("존재하지 않는 콘텐츠를 수정하면 CONTENT_NOT_FOUND 예외를 던진다")
    void fail_throwsContentNotFound_whenContentMissing() {
      // given
      UUID contentId = UUID.randomUUID();
      ContentUpdateRequest request = new ContentUpdateRequest("새제목", "새설명", null);
      given(contentRepository.findByIdAndDeletedAtIsNull(contentId)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> contentService.update(contentId, request, null))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.CONTENT_NOT_FOUND);

      then(fileStorage).should(never()).store(any());
      then(contentMapper).should(never()).toDto(any(), anyList(), anyLong());
    }

    @Test
    @DisplayName("썸네일/태그 없이 제목/설명만 수정하면 기존 태그를 유지한 채 반영된다")
    void success_updatesTitleAndDescriptionOnly() {
      // given
      UUID contentId = UUID.randomUUID();
      Content content = contentWithId(contentId);
      ContentUpdateRequest request = new ContentUpdateRequest("새제목", "새설명", null);
      List<Tag> existingTags = List.of(new Tag(content, "SF"));
      ContentDto expected = mockDto(contentId, List.of("SF"), 3L);

      given(contentRepository.findByIdAndDeletedAtIsNull(contentId))
          .willReturn(Optional.of(content));
      given(tagRepository.findByContentIdAndDeletedAtIsNull(contentId)).willReturn(existingTags);
      given(watcherCountService.count(contentId)).willReturn(3L);
      given(contentMapper.toDto(content, existingTags, 3L)).willReturn(expected);

      // when
      ContentDto actual = contentService.update(contentId, request, null);

      // then
      assertThat(actual).isEqualTo(expected);
      assertThat(content.getTitle()).isEqualTo("새제목");
      assertThat(content.getDescription()).isEqualTo("새설명");
      assertThat(content.getThumbnailUrl()).isEqualTo("old-url"); // 썸네일 미변경
      then(fileStorage).should(never()).store(any());
    }

    @Test
    @DisplayName("새 썸네일이 비어있지 않으면 저장 후 썸네일 URL을 교체한다")
    void success_replacesThumbnail_whenProvided() {
      // given
      UUID contentId = UUID.randomUUID();
      Content content = contentWithId(contentId);
      ContentUpdateRequest request = new ContentUpdateRequest(null, null, null);
      MultipartFile thumbnail = mockThumbnail(false);

      given(contentRepository.findByIdAndDeletedAtIsNull(contentId))
          .willReturn(Optional.of(content));
      given(fileStorage.store(thumbnail)).willReturn("new-url");
      given(tagRepository.findByContentIdAndDeletedAtIsNull(contentId)).willReturn(List.of());
      given(watcherCountService.count(contentId)).willReturn(0L);
      given(contentMapper.toDto(eq(content), anyList(), eq(0L)))
          .willReturn(mockDto(contentId, List.of(), 0L));

      // when
      contentService.update(contentId, request, thumbnail);

      // then
      assertThat(content.getThumbnailUrl()).isEqualTo("new-url");
      then(fileStorage).should().store(thumbnail);
    }

    @Test
    @DisplayName("빈 썸네일이 전달되면 저장하지 않고 기존 썸네일을 유지한다")
    void success_keepsThumbnail_whenEmptyThumbnail() {
      // given
      UUID contentId = UUID.randomUUID();
      Content content = contentWithId(contentId);
      ContentUpdateRequest request = new ContentUpdateRequest(null, null, null);
      MultipartFile thumbnail = mockThumbnail(true); // isEmpty == true

      given(contentRepository.findByIdAndDeletedAtIsNull(contentId))
          .willReturn(Optional.of(content));
      given(tagRepository.findByContentIdAndDeletedAtIsNull(contentId)).willReturn(List.of());
      given(watcherCountService.count(contentId)).willReturn(0L);
      given(contentMapper.toDto(eq(content), anyList(), eq(0L)))
          .willReturn(mockDto(contentId, List.of(), 0L));

      // when
      contentService.update(contentId, request, thumbnail);

      // then
      assertThat(content.getThumbnailUrl()).isEqualTo("old-url");
      then(fileStorage).should(never()).store(any());
    }

    @Test
    @DisplayName("tags가 전달되면 기존 태그를 논리 삭제/flush 후 새 태그로 교체한다")
    void success_replacesTags_whenTagsProvided() {
      // given
      UUID contentId = UUID.randomUUID();
      Content content = contentWithId(contentId);
      ContentUpdateRequest request = new ContentUpdateRequest(null, null, List.of("액션"));
      Tag oldTag1 = new Tag(content, "SF");
      Tag oldTag2 = new Tag(content, "스릴러");

      given(contentRepository.findByIdAndDeletedAtIsNull(contentId))
          .willReturn(Optional.of(content));
      // replaceTags 내부의 기존 태그 조회
      given(tagRepository.findByContentIdAndDeletedAtIsNull(contentId))
          .willReturn(List.of(oldTag1, oldTag2));
      given(tagRepository.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));
      given(watcherCountService.count(contentId)).willReturn(0L);
      given(contentMapper.toDto(eq(content), anyList(), eq(0L)))
          .willReturn(mockDto(contentId, List.of("액션"), 0L));

      // when
      contentService.update(contentId, request, null);

      // then: 기존 태그는 논리 삭제되고 flush 후 새 태그 저장
      assertThat(oldTag1.isDeleted()).isTrue();
      assertThat(oldTag2.isDeleted()).isTrue();
      then(tagRepository).should().flush();
      then(tagRepository).should().saveAll(tagListCaptor.capture());
      assertThat(tagListCaptor.getValue()).extracting(Tag::getName).containsExactly("액션");
    }
  }

  @Nested
  @DisplayName("delete - 콘텐츠 삭제")
  class Delete {

    @Test
    @DisplayName("존재하지 않는 콘텐츠를 삭제하면 CONTENT_NOT_FOUND 예외를 던진다")
    void fail_throwsContentNotFound_whenContentMissing() {
      // given
      UUID contentId = UUID.randomUUID();
      given(contentRepository.findByIdAndDeletedAtIsNull(contentId)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> contentService.delete(contentId))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.CONTENT_NOT_FOUND);

      then(tagRepository).should(never()).findByContentIdAndDeletedAtIsNull(any());
    }

    @Test
    @DisplayName("콘텐츠와 활성 태그를 모두 논리 삭제한다")
    void success_softDeletesContentAndTags() {
      // given
      UUID contentId = UUID.randomUUID();
      Content content = contentWithId(contentId);
      Tag tag1 = new Tag(content, "SF");
      Tag tag2 = new Tag(content, "스릴러");

      given(contentRepository.findByIdAndDeletedAtIsNull(contentId))
          .willReturn(Optional.of(content));
      given(tagRepository.findByContentIdAndDeletedAtIsNull(contentId))
          .willReturn(List.of(tag1, tag2));

      // when
      contentService.delete(contentId);

      // then
      assertThat(content.isDeleted()).isTrue();
      assertThat(tag1.isDeleted()).isTrue();
      assertThat(tag2.isDeleted()).isTrue();
    }
  }

  @Nested
  @DisplayName("getContents - 콘텐츠 목록 조회")
  class GetContents {}

  // ===

  private MultipartFile mockThumbnail(boolean empty) {
    MultipartFile file = mock(MultipartFile.class);
    if (empty) {
      given(file.isEmpty()).willReturn(true);
    }
    return file;
  }

  private ContentDto mockDto(UUID id, List<String> tags, long watcherCount) {
    return new ContentDto(
        id, ContentType.MOVIE, "title", "desc", "url", tags, 0.0, 0, watcherCount);
  }
}
