package com.team02.mopl.domain.content.service;

import com.team02.mopl.domain.content.dto.ContentCreateRequest;
import com.team02.mopl.domain.content.dto.ContentDto;
import com.team02.mopl.domain.content.dto.ContentSearchCondition;
import com.team02.mopl.domain.content.dto.ContentSearchRequest;
import com.team02.mopl.domain.content.dto.ContentUpdateRequest;
import com.team02.mopl.domain.content.entity.Content;
import com.team02.mopl.domain.content.entity.Tag;
import com.team02.mopl.domain.content.enums.SortBy;
import com.team02.mopl.domain.content.exception.ContentNotFoundException;
import com.team02.mopl.domain.content.mapper.ContentMapper;
import com.team02.mopl.domain.content.repository.ContentRepository;
import com.team02.mopl.domain.content.repository.TagRepository;
import com.team02.mopl.domain.content.util.ContentCursorConverter;
import com.team02.mopl.global.dto.CursorResponse;
import com.team02.mopl.global.enums.SortDirection;
import com.team02.mopl.global.storage.FileStorage;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContentService {

  private final ContentRepository contentRepository;
  private final TagRepository tagRepository;
  private final ContentMapper contentMapper;
  private final WatcherCountService watcherCountService;
  private final FileStorage fileStorage;

  // 썸네일 미제공 시 사용할 기본값. 앱 내장 정적 리소스(static/images/default-thumbnail.svg) URL.
  // 생성 폼 - 썸네일을 클라이언트단에서 필수로 강제하고 있으나 직접 API 호출 시 방어 목적.
  // project-mopl-fe-1.0.2/src/pages/contents/components/ContentFormDialog.tsx 98-101 lines
  @Value("${app.storage.default-thumbnail-url:}")
  private String defaultThumbnailUrl;

  // [어드민] 콘텐츠 생성
  @Transactional
  public ContentDto create(ContentCreateRequest request, MultipartFile thumbnail) {
    String thumbnailUrl =
        (thumbnail != null && !thumbnail.isEmpty())
            ? fileStorage.store(thumbnail)
            : defaultThumbnailUrl;
    Content content =
        new Content(request.type(), request.title(), request.description(), thumbnailUrl);
    contentRepository.save(content);

    List<Tag> tags = addTags(content, request.tags());
    log.info(
        "content.created contentId={} type={} tagCount={}",
        content.getId(),
        request.type(),
        tags.size());
    return contentMapper.toDto(content, tags, 0L);
  }

  // 콘텐츠 단건 조회
  @Transactional(readOnly = true)
  public ContentDto get(UUID contentId) {
    Content content = findActiveOrThrow(contentId);
    List<Tag> tags = tagRepository.findByContentIdAndDeletedAtIsNull(contentId);
    long watcherCount = watcherCountService.count(contentId);
    return contentMapper.toDto(content, tags, watcherCount);
  }

  // 콘텐츠 목록 조회 (QueryDSL 동적 필터 + 동적 정렬 + 복합 커서)
  @Transactional(readOnly = true)
  public CursorResponse<ContentDto> getContents(ContentSearchRequest request) {
    // 정렬 기준 미지정 시 인기순(WATCHER_COUNT)으로 기본 정렬
    SortBy sortBy = request.sortBy() != null ? request.sortBy() : SortBy.WATCHER_COUNT;
    // 정렬 방향 미지정 시 내림차순(최신순) 기본값
    SortDirection direction =
        request.sortDirection() != null ? request.sortDirection() : SortDirection.DESCENDING;
    boolean asc = direction == SortDirection.ASCENDING;

    // 필터 정규화
    String keyword =
        (request.keywordLike() != null && !request.keywordLike().isBlank())
            ? request.keywordLike().trim()
            : null;

    // QueryDSL 동적 쿼리에 넘길 검색 조건 객체 조립
    ContentSearchCondition condition =
        new ContentSearchCondition(
            request.typeEqual(),
            keyword,
            normalizeTags(request.tagsIn()),
            sortBy,
            asc,
            ContentCursorConverter.toSortKey(sortBy, request.cursor()),
            request.idAfter(),
            request.fetchLimit());

    // limit + 1 적재분을 잘라 hasNext 판정 (커서 페이지네이션)
    List<Content> rows = contentRepository.search(condition);
    int size = request.normalizedLimit();
    boolean hasNext = rows.size() > size;
    List<Content> pageContents = hasNext ? rows.subList(0, size) : rows;
    // 이번 페이지에 있는 contentId를 한 번에 다 뽑는다
    List<UUID> pageIds = pageContents.stream().map(Content::getId).toList();

    // 태그, watcherCount N+1 방지 일괄 조회 후 콘텐츠별 그룹핑
    Map<UUID, List<Tag>> tagsByContent =
        pageIds.isEmpty()
            ? Map.of()
            : tagRepository.findByContentIdInAndDeletedAtIsNull(pageIds).stream() // 태그를 전부 가져오고
                .collect(
                    Collectors.groupingBy(
                        tag -> tag.getContent().getId())); // 가져온 태그들을 contentId 기준으로 묶음
    // contentIds를 통해 모든 시청자 수를 조회한 후 Map으로 반환 (contentId, 시청자 수)
    Map<UUID, Long> watcherCounts = watcherCountService.countByContentIds(pageIds);

    long totalCount = contentRepository.countBySearch(condition);

    // 페이지에 속한 콘텐츠들을 응답 DTO 리스트로 매핑
    List<ContentDto> data =
        pageContents.stream()
            .map(
                content ->
                    contentMapper.toDto(
                        content,
                        tagsByContent.getOrDefault(content.getId(), List.of()),
                        watcherCounts.getOrDefault(content.getId(), 0L)))
            .toList();

    // 다음 페이지 커서 계산
    String nextCursor = null;
    UUID nextIdAfter = null;
    if (hasNext && !pageContents.isEmpty()) {
      Content last = pageContents.get(pageContents.size() - 1);
      nextCursor =
          ContentCursorConverter.toCursor(
              sortBy, last, watcherCounts.getOrDefault(last.getId(), 0L));
      nextIdAfter = last.getId();
    }

    return new CursorResponse<>(
        data, nextCursor, nextIdAfter, hasNext, totalCount, sortBy.getValue(), direction.name());
  }

  // [어드민] 콘텐츠 수정
  // TODO: 교체된 구 썸네일은 즉시 삭제하지 않음. DB 미추적이므로 디스크 스캔 배치로 고아 파일 정리
  @Transactional
  public ContentDto update(UUID contentId, ContentUpdateRequest request, MultipartFile thumbnail) {
    Content content = findActiveOrThrow(contentId);
    content.update(request.title(), request.description());

    if (thumbnail != null && !thumbnail.isEmpty()) {
      content.changeThumbnailUrl(fileStorage.store(thumbnail));
    }

    List<Tag> tags =
        (request.tags() != null)
            ? replaceTags(content, request.tags())
            : tagRepository.findByContentIdAndDeletedAtIsNull(contentId);

    long watcherCount = watcherCountService.count(contentId);
    log.info(
        "content.updated contentId={} tagCount={} thumbnailChanged={}",
        contentId,
        tags.size(),
        (thumbnail != null && !thumbnail.isEmpty()));
    return contentMapper.toDto(content, tags, watcherCount);
  }

  // [어드민] 콘텐츠 삭제
  // TODO: 썸네일 물리 삭제는 배치로 처리 (논리 삭제 후 N일 경과 콘텐츠의 thumbnailUrl 정리)
  @Transactional
  public void delete(UUID contentId) {
    Content content = findActiveOrThrow(contentId);
    content.delete();
    List<Tag> tags = tagRepository.findByContentIdAndDeletedAtIsNull(contentId);
    tags.forEach(Tag::delete);

    log.info("content.deleted contentId={} deletedTagCount={}", contentId, tags.size());
  }

  // ===

  // null/공백 태그 제거 + trim + 중복 제거
  private static List<String> normalizeTags(List<String> tags) {
    if (tags == null) { // 태그 목록 자체가 null이면 (태그 필터 미지정) 빈 리스트로 변환
      return List.of();
    }
    return tags.stream()
        .filter(tag -> tag != null && !tag.isBlank()) // 원소가 null 이거나 공백뿐인 태그는 제거
        .map(String::trim) // 앞뒤 공백 제거
        .distinct() // trim 후 동일해진 태그 중복 제거
        .toList();
  }

  // TODO: 태그로 검색 (장르 검색) 기능 추가할 경우 엔티티 관계 N:M으로 변경 요망
  // 현재는 콘텐츠가 가지고 있는 태그 목록 보여주는 조회만 일어나므로 현재 구조 유지
  private List<Tag> addTags(Content content, List<String> names) {
    if (names == null || names.isEmpty()) {
      return List.of();
    }
    // 입력된 태그 순서 유지를 위해 LinkedHashSet 사용 (입력 순서를 유지하면서 중복만 제거 목적)
    LinkedHashSet<String> distinct =
        names.stream()
            .filter(name -> name != null && !name.isBlank())
            .map(String::trim)
            .collect(Collectors.toCollection(LinkedHashSet::new));

    // 현재 활성 태그 이름을 한 번만 조회해 메모리에서 중복 제거 (태그별 exists 쿼리 N회 제거)
    Set<String> existing =
        tagRepository.findByContentIdAndDeletedAtIsNull(content.getId()).stream()
            .map(Tag::getName)
            .collect(Collectors.toSet());

    List<Tag> newTags =
        distinct.stream()
            .filter(name -> !existing.contains(name)) // 새로 추가할 태그만 남김
            .map(name -> new Tag(content, name))
            .collect(Collectors.toList());

    return tagRepository.saveAll(newTags);
  }

  private Content findActiveOrThrow(UUID contentId) {
    return contentRepository
        .findByIdAndDeletedAtIsNull(contentId)
        .orElseThrow(ContentNotFoundException::new);
  }

  private List<Tag> replaceTags(Content content, List<String> names) {
    // 조회 (영속 상태) -> forEach + delete (영속 엔티티의 deletedAt 변경, 더티 체킹 예약)
    tagRepository.findByContentIdAndDeletedAtIsNull(content.getId()).forEach(Tag::delete);
    tagRepository.flush(); // 변경분을 DB에 UPDATED로 반영 (그래야 새로운 태그 정상적으로 저장됨)
    List<Tag> newTags = addTags(content, names);
    log.debug("content.tags_replaced contentId={} addedCount={}", content.getId(), newTags.size());
    return newTags;
  }
}
