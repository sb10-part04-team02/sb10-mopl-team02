package com.team02.mopl.domain.content.service;

import com.team02.mopl.domain.content.dto.ContentCreateRequest;
import com.team02.mopl.domain.content.dto.ContentDto;
import com.team02.mopl.domain.content.dto.ContentSearchRequest;
import com.team02.mopl.domain.content.dto.ContentUpdateRequest;
import com.team02.mopl.domain.content.entity.Content;
import com.team02.mopl.domain.content.entity.Tag;
import com.team02.mopl.domain.content.mapper.ContentMapper;
import com.team02.mopl.domain.content.repository.ContentRepository;
import com.team02.mopl.domain.content.repository.TagRepository;
import com.team02.mopl.global.dto.CursorResponse;
import com.team02.mopl.global.exception.BusinessException;
import com.team02.mopl.global.exception.ErrorCode;
import com.team02.mopl.global.storage.FileStorage;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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

  // 썸네일 미제공 시 사용할 기본값.
  // 생성 폼 - 썸네일을 클라이언트단에서 필수로 강제하고 있으나 직접 API 호출 시 방어 목적.
  // project-mopl-fe-1.0.2/src/pages/contents/components/ContentFormDialog.tsx 98-101 lines
  // TODO: S3 스토리지 구현 이슈에서 실제 기본 이미지의 절대 URL로 교체 예정
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

  // 콘텐츠 목록 조회 (커서 페이지네이션)
  @Transactional(readOnly = true)
  public CursorResponse<ContentDto> getContents(ContentSearchRequest request) {
    throw new UnsupportedOperationException("TODO: 콘텐츠 목록 조회 미구현");
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

    List<Tag> saved = new ArrayList<>();
    for (String name : distinct) {
      if (tagRepository.existsByContentIdAndNameAndDeletedAtIsNull(content.getId(), name)) {
        continue;
      }
      saved.add(tagRepository.save(new Tag(content, name)));
    }
    return saved;
  }

  private Content findActiveOrThrow(UUID contentId) {
    return contentRepository
        .findByIdAndDeletedAtIsNull(contentId)
        .orElseThrow(() -> new BusinessException(ErrorCode.CONTENT_NOT_FOUND));
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
