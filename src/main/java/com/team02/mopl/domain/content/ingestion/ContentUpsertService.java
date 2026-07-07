package com.team02.mopl.domain.content.ingestion;

import com.team02.mopl.domain.content.entity.Content;
import com.team02.mopl.domain.content.entity.Tag;
import com.team02.mopl.domain.content.repository.ContentRepository;
import com.team02.mopl.domain.content.repository.TagRepository;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 외부 수집 콘텐츠의 멱등 upsert 저장.
 *
 * <p>트랜잭션 경계는 건별이다. 불량 데이터 1건이 수집 실행 전체를 롤백시키지 않도록 호출측은 트랜잭션 없이 건별로 호출하고 예외를 집계한다. 동시 실행 레이스로 인한 중복
 * insert는 (source, external_id) 유니크 인덱스가 DB에서 차단하며, 이때의 DataIntegrityViolationException 처리도 호출측
 * 몫이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentUpsertService {

  private final ContentRepository contentRepository;
  private final TagRepository tagRepository;

  @Transactional
  public UpsertResult upsert(ExternalContentData data) {
    Optional<Content> existing =
        contentRepository.findBySourceAndExternalId(data.source(), data.externalId());

    if (existing.isEmpty()) {
      Content content =
          contentRepository.save(
              Content.createExternal(
                  data.source(),
                  data.externalId(),
                  data.contentType(),
                  data.title(),
                  data.description(),
                  data.thumbnailUrl()));
      mergeAddTags(content, data);
      return UpsertResult.INSERTED;
    }

    Content content = existing.get();
    if (content.isDeleted()) {
      // 어드민이 삭제한 콘텐츠는 재수집이 되살리지 않는다 (복구가 필요해지면 여기서 restore 정책으로 분기)
      log.info("삭제된 콘텐츠라 수집을 건너뜁니다. source={}, externalId={}", data.source(), data.externalId());
      return UpsertResult.SKIPPED;
    }

    // 부분 갱신: 빈 값은 기존 값을 덮지 않고(hasText 방어), 리뷰 집계(averageRating/reviewCount)는 건드리지 않는다
    content.update(data.title(), data.description());
    content.changeThumbnailUrl(data.thumbnailUrl());
    mergeAddTags(content, data);
    return UpsertResult.UPDATED;
  }

  // merge-add: 기존 활성 태그에 없는 이름만 추가한다. 어드민이 수동으로 붙인 태그를 수집이 지우지 않기 위해 삭제는 하지 않는다
  private void mergeAddTags(Content content, ExternalContentData data) {
    Set<String> existingNames =
        tagRepository.findByContentIdAndDeletedAtIsNull(content.getId()).stream()
            .map(Tag::getName)
            .collect(Collectors.toSet());
    data.tags().stream()
        .distinct()
        .filter(name -> !existingNames.contains(name))
        .forEach(name -> tagRepository.save(new Tag(content, name)));
  }
}
