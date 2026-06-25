package com.team02.mopl.domain.content.repository;

import com.team02.mopl.domain.content.entity.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TagRepository extends JpaRepository<Tag, UUID> {

  // 콘텐츠별 활성 태그 조회
  // SELECT t.* FROM tags t WHERE t.content_id = ? AND t.deleted_at IS NULL;
  List<Tag> findByContentIdAndDeletedAtIsNull(UUID contentId);

  // 활성 태그 중복 여부(partial unique uk_tags_content_name 정합)
  // SELECT t.* FROM tags t
  // WHERE t.content_id = ?
  //  AND t.name = ?
  //  AND t.deleted_at IS NULL
  // LIMIT 1;
  boolean existsByContentIdAndNameAndDeletedAtIsNull(UUID contentId, String name);
}
