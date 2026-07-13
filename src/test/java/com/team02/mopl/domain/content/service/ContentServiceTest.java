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
import com.team02.mopl.domain.content.dto.ContentSearchCondition;
import com.team02.mopl.domain.content.dto.ContentSearchRequest;
import com.team02.mopl.domain.content.dto.ContentUpdateRequest;
import com.team02.mopl.domain.content.entity.Content;
import com.team02.mopl.domain.content.entity.Tag;
import com.team02.mopl.domain.content.enums.ContentType;
import com.team02.mopl.domain.content.enums.SortBy;
import com.team02.mopl.domain.content.exception.ContentNotFoundException;
import com.team02.mopl.domain.content.mapper.ContentMapper;
import com.team02.mopl.domain.content.repository.ContentRepository;
import com.team02.mopl.domain.content.repository.TagRepository;
import com.team02.mopl.global.dto.CursorResponse;
import com.team02.mopl.global.enums.SortDirection;
import com.team02.mopl.global.exception.ErrorCode;
import com.team02.mopl.global.exception.InvalidCursorRequestException;
import com.team02.mopl.global.storage.FileStorage;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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

  @Captor ArgumentCaptor<List<Tag>> mapperTagListCaptor;

  @Captor ArgumentCaptor<ContentSearchCondition> conditionCaptor;

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
          .isInstanceOf(ContentNotFoundException.class)
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
      MultipartFile thumbnail = mockThumbnail(true);
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
      MultipartFile thumbnail = mockThumbnail(true);
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
      MultipartFile thumbnail = mockThumbnail(true);

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

    @Test
    @DisplayName("기본 썸네일 설정이 비어 있어도 500 없이 코드 레벨 fallback URL로 정상 저장한다")
    void success_usesFallbackThumbnail_whenDefaultUrlBlank() {
      // given: default-thumbnail-url 설정 누락 상황 재현 (이슈 #340의 실제 500 유발 조건)
      ReflectionTestUtils.setField(contentService, "defaultThumbnailUrl", "");
      ContentCreateRequest request =
          new ContentCreateRequest(ContentType.MOVIE, "인셉션", "꿈 속의 꿈", List.of());
      given(contentMapper.toDto(any(Content.class), anyList(), eq(0L)))
          .willReturn(mockDto(UUID.randomUUID(), List.of(), 0L));

      // when
      contentService.create(request, null);

      // then: Content blank 검증(500)에 걸리지 않고 코드 레벨 fallback URL이 저장됨
      then(fileStorage).should(never()).store(any());
      then(contentRepository).should().save(contentCaptor.capture());
      assertThat(contentCaptor.getValue().getThumbnailUrl())
          .isEqualTo("/images/default-thumbnail.svg");
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
          .isInstanceOf(ContentNotFoundException.class)
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
      // 매퍼에 전달된 태그가 (삭제된 구 태그가 아닌) 새로 교체된 태그인지 검증
      then(contentMapper).should().toDto(eq(content), mapperTagListCaptor.capture(), eq(0L));
      assertThat(mapperTagListCaptor.getValue()).extracting(Tag::getName).containsExactly("액션");
    }

    @Test
    @DisplayName("tags가 빈 리스트면 기존 태그를 모두 논리 삭제하고 새 태그는 저장하지 않는다")
    void success_removesAllTags_whenTagsEmptyList() {
      // given
      UUID contentId = UUID.randomUUID();
      Content content = contentWithId(contentId);
      ContentUpdateRequest request = new ContentUpdateRequest(null, null, List.of());
      Tag oldTag = new Tag(content, "SF");

      given(contentRepository.findByIdAndDeletedAtIsNull(contentId))
          .willReturn(Optional.of(content));
      given(tagRepository.findByContentIdAndDeletedAtIsNull(contentId)).willReturn(List.of(oldTag));
      given(watcherCountService.count(contentId)).willReturn(0L);
      given(contentMapper.toDto(eq(content), anyList(), eq(0L)))
          .willReturn(mockDto(contentId, List.of(), 0L));

      // when
      contentService.update(contentId, request, null);

      // then: tags == null이면 유지되지만, 빈 리스트면 전부 제거된다
      assertThat(oldTag.isDeleted()).isTrue();
      then(tagRepository).should().flush();
      then(tagRepository).should(never()).saveAll(any());
      then(contentMapper).should().toDto(eq(content), mapperTagListCaptor.capture(), eq(0L));
      assertThat(mapperTagListCaptor.getValue()).isEmpty();
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
          .isInstanceOf(ContentNotFoundException.class)
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
  class GetContents {

    // 이 테스트 그룹에서는 type과 idAfter를 검증 대상으로 삼지 않으므로 null로 고정
    private ContentSearchRequest request(
        String keyword,
        List<String> tags,
        String cursor,
        int limit,
        SortDirection direction,
        SortBy sortBy) {
      return new ContentSearchRequest(null, keyword, tags, cursor, null, limit, direction, sortBy);
    }

    private Content content(UUID id, Instant createdAt, double averageRating) {
      Content content = new Content(ContentType.MOVIE, "제목", "설명", "url");
      ReflectionTestUtils.setField(content, "id", id);
      ReflectionTestUtils.setField(content, "createdAt", createdAt);
      ReflectionTestUtils.setField(content, "averageRating", averageRating);
      return content;
    }

    @Test
    @DisplayName("sortBy/sortDirection 미지정 시 WATCHER_COUNT/DESCENDING으로 조회하고 응답에 그대로 반영한다")
    void appliesDefaults_whenSortNotGiven() {
      // given
      ContentSearchRequest req = request(null, null, null, 20, null, null);
      given(contentRepository.search(conditionCaptor.capture())).willReturn(List.of());
      given(contentRepository.countBySearch(any())).willReturn(0L);

      // when
      CursorResponse<ContentDto> response = contentService.getContents(req);

      // then
      ContentSearchCondition condition = conditionCaptor.getValue();
      assertThat(condition.sortBy()).isEqualTo(SortBy.WATCHER_COUNT);
      assertThat(condition.asc()).isFalse();
      assertThat(condition.limit()).isEqualTo(21); // fetchLimit = normalizedLimit + 1
      assertThat(response.sortBy()).isEqualTo("watcherCount");
      assertThat(response.sortDirection()).isEqualTo("DESCENDING");
    }

    @Test
    @DisplayName("sortBy/sortDirection 지정 시 그대로 조건과 응답에 반영한다(ASCENDING -> asc=true)")
    void appliesGivenSort() {
      // given
      ContentSearchRequest req =
          request(null, null, null, 20, SortDirection.ASCENDING, SortBy.CREATED_AT);
      given(contentRepository.search(conditionCaptor.capture())).willReturn(List.of());
      given(contentRepository.countBySearch(any())).willReturn(0L);

      // when
      CursorResponse<ContentDto> response = contentService.getContents(req);

      // then
      ContentSearchCondition condition = conditionCaptor.getValue();
      assertThat(condition.sortBy()).isEqualTo(SortBy.CREATED_AT);
      assertThat(condition.asc()).isTrue();
      assertThat(response.sortBy()).isEqualTo("createdAt");
      assertThat(response.sortDirection()).isEqualTo("ASCENDING");
    }

    @Test
    @DisplayName("키워드는 앞뒤 공백을 제거하고, null이거나 공백뿐이면 null로 정규화한다")
    void normalizesKeyword() {
      // given
      given(contentRepository.search(conditionCaptor.capture())).willReturn(List.of());
      given(contentRepository.countBySearch(any())).willReturn(0L);

      // when & then: 앞뒤 공백 제거
      contentService.getContents(request("  hello  ", null, null, 20, null, null));
      assertThat(conditionCaptor.getValue().keyword()).isEqualTo("hello");

      // when & then: 공백뿐이면 null
      contentService.getContents(request("   ", null, null, 20, null, null));
      assertThat(conditionCaptor.getValue().keyword()).isNull();

      // when & then: null이면 null
      contentService.getContents(request(null, null, null, 20, null, null));
      assertThat(conditionCaptor.getValue().keyword()).isNull();
    }

    @Test
    @DisplayName("태그는 null/공백 원소 제거 + trim + 중복 제거되며, null 입력은 빈 리스트가 된다")
    void normalizesTags() {
      // given
      given(contentRepository.search(conditionCaptor.capture())).willReturn(List.of());
      given(contentRepository.countBySearch(any())).willReturn(0L);

      // 입력: ["액션", " 액션 ", "  ", "", null, "드라마"]
      // when & then: null/공백 원소 제거 + trim + 중복 제거
      contentService.getContents(
          request(null, Arrays.asList("액션", " 액션 ", "  ", "", null, "드라마"), null, 20, null, null));
      assertThat(conditionCaptor.getValue().tags()).containsExactly("액션", "드라마");

      // when & then: null 입력은 빈 리스트
      contentService.getContents(request(null, null, null, 20, null, null));
      assertThat(conditionCaptor.getValue().tags()).isEmpty();
    }

    @Test
    @DisplayName("repository가 fetchLimit(=size+1)건을 반환하면 hasNext=true이고 여분 1건을 잘라 size개만 매핑한다")
    void hasNextTrue_whenExtraRowFetched() {
      // given: limit 2 -> fetchLimit 3, search가 3건 반환
      UUID id1 = UUID.randomUUID();
      UUID id2 = UUID.randomUUID();
      UUID id3 = UUID.randomUUID();
      Instant now = Instant.parse("2026-06-30T10:00:00Z");
      given(contentRepository.search(any()))
          .willReturn(
              List.of(content(id1, now, 4.0), content(id2, now, 3.0), content(id3, now, 2.0)));
      given(contentRepository.countBySearch(any())).willReturn(10L);
      // 이 테스트의 관심사가 아니므로 빈 리스트 반환
      given(tagRepository.findByContentIdInAndDeletedAtIsNull(any())).willReturn(List.of());
      given(watcherCountService.countByContentIds(any())).willReturn(Map.of(id1, 9L, id2, 5L));
      given(contentMapper.toDto(any(Content.class), anyList(), anyLong()))
          .willReturn(mockDto(id1, List.of(), 0L));

      // when
      CursorResponse<ContentDto> response =
          contentService.getContents(request(null, null, null, 2, null, null));

      // then
      assertThat(response.hasNext()).isTrue(); // 다음 페이지 존재
      assertThat(response.data()).hasSize(2);
      assertThat(response.totalCount()).isEqualTo(10L);
      // 여분 1건(id3)은 버려지고, 마지막 페이지 행(id2) 기준으로 커서가 생성된다
      assertThat(response.nextIdAfter()).isEqualTo(id2);
      assertThat(response.nextCursor()).isEqualTo("5"); // WATCHER_COUNT: watcherCounts.get(id2)
    }

    @Test
    @DisplayName("repository가 size 이하로 반환하면 hasNext=false이고 nextCursor/nextIdAfter는 null이다")
    void hasNextFalse_whenNoExtraRow() {
      // given: limit 2, search가 2건만 반환
      UUID id1 = UUID.randomUUID();
      UUID id2 = UUID.randomUUID();
      Instant now = Instant.now();
      given(contentRepository.search(any()))
          .willReturn(List.of(content(id1, now, 4.0), content(id2, now, 3.0)));
      given(contentRepository.countBySearch(any())).willReturn(2L);
      given(tagRepository.findByContentIdInAndDeletedAtIsNull(any())).willReturn(List.of());
      given(watcherCountService.countByContentIds(any())).willReturn(Map.of());
      given(contentMapper.toDto(any(Content.class), anyList(), anyLong()))
          .willReturn(mockDto(id1, List.of(), 0L));

      // when
      CursorResponse<ContentDto> response =
          contentService.getContents(request(null, null, null, 2, null, null));

      // then
      assertThat(response.hasNext()).isFalse(); // 다음 페이지 없음
      assertThat(response.data()).hasSize(2);
      assertThat(response.nextCursor()).isNull(); // 마지막 페이지이므로 커서 관련 값은 모두 null
      assertThat(response.nextIdAfter()).isNull();
    }

    @Test
    @DisplayName("결과가 비어 있으면 빈 데이터/0건/커서 null을 반환하고 태그 일괄 조회를 호출하지 않는다")
    void emptyResult_shortCircuitsTagFetch() {
      // given
      given(contentRepository.search(any())).willReturn(List.of());
      given(contentRepository.countBySearch(any())).willReturn(0L);

      // when
      CursorResponse<ContentDto> response =
          contentService.getContents(request(null, null, null, 20, null, null));

      // then
      assertThat(response.data()).isEmpty();
      assertThat(response.hasNext()).isFalse();
      assertThat(response.totalCount()).isZero();
      assertThat(response.nextCursor()).isNull();
      assertThat(response.nextIdAfter()).isNull();
      // 콘텐츠가 0건이면 태그를 조회할 대상도 없으므로 태그 일괄 조회를 하지 않아야 한다
      then(tagRepository).should(never()).findByContentIdInAndDeletedAtIsNull(any());
    }

    @Test
    @DisplayName("CREATED_AT 정렬에서는 마지막 행의 createdAt 문자열이 nextCursor가 된다")
    void nextCursorUsesCreatedAt_whenSortByCreatedAt() {
      // given: limit 1 -> fetchLimit 2, search 2건 -> hasNext, last = 첫 번째 행
      UUID id1 = UUID.randomUUID();
      UUID id2 = UUID.randomUUID();
      Instant lastCreatedAt = Instant.parse("2026-06-30T10:00:00Z");
      given(contentRepository.search(any()))
          .willReturn(List.of(content(id1, lastCreatedAt, 4.0), content(id2, Instant.now(), 3.0)));
      given(contentRepository.countBySearch(any())).willReturn(5L);
      given(tagRepository.findByContentIdInAndDeletedAtIsNull(any())).willReturn(List.of());
      given(watcherCountService.countByContentIds(any())).willReturn(Map.of());
      given(contentMapper.toDto(any(Content.class), anyList(), anyLong()))
          .willReturn(mockDto(id1, List.of(), 0L));

      // when
      CursorResponse<ContentDto> response =
          contentService.getContents(request(null, null, null, 1, null, SortBy.CREATED_AT));

      // then
      assertThat(response.hasNext()).isTrue();
      // 커서의 보조 키(tie-breaker)인 nextIdAfter는 잘라낸 뒤 마지막 행 id1의 id가 된다
      assertThat(response.nextIdAfter()).isEqualTo(id1);
      assertThat(response.nextCursor()).isEqualTo(lastCreatedAt.toString());
    }

    @Test
    @DisplayName("cursor만 있고 idAfter가 없으면 INVALID_CURSOR_REQUEST로 거부하고 repository를 호출하지 않는다")
    void rejectsHalfCursor_whenIdAfterMissing() {
      // given
      ContentSearchRequest req = request(null, null, "somecursor", 20, null, null);

      // when & then
      assertThatThrownBy(() -> contentService.getContents(req))
          .isInstanceOf(InvalidCursorRequestException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.INVALID_CURSOR_REQUEST);

      then(contentRepository).should(never()).search(any());
    }

    @Test
    @DisplayName("idAfter만 있고 cursor가 없으면 INVALID_CURSOR_REQUEST로 거부하고 repository를 호출하지 않는다")
    void rejectsHalfCursor_whenCursorMissing() {
      // given
      ContentSearchRequest req =
          new ContentSearchRequest(null, null, null, null, UUID.randomUUID(), 20, null, null);

      // when & then
      assertThatThrownBy(() -> contentService.getContents(req))
          .isInstanceOf(InvalidCursorRequestException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.INVALID_CURSOR_REQUEST);

      then(contentRepository).should(never()).search(any());
    }
  }

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
