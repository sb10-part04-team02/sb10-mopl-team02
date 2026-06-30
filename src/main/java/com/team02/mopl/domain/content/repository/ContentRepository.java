package com.team02.mopl.domain.content.repository;

import com.team02.mopl.domain.content.entity.Content;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentRepository extends JpaRepository<Content, UUID>, ContentRepositoryCustom {

  // 활성(논리 삭제되지 않은) 콘텐츠 단건 조회
  // SELECT c.* FROM contents c WHERE c.id = ? AND c.deleted_at IS NULL;
  Optional<Content> findByIdAndDeletedAtIsNull(UUID id);
}
