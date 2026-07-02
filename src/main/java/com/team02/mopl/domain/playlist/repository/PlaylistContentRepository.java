package com.team02.mopl.domain.playlist.repository;

import com.team02.mopl.domain.playlist.entity.PlaylistContent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaylistContentRepository extends JpaRepository<PlaylistContent, UUID> {

  // 플레이리스트에 추가된 순서(createdAt)대로 반환해 콘텐츠 노출 순서를 고정 (동률 시 id로 타이브레이크)
  List<PlaylistContent> findByPlaylistIdOrderByCreatedAtAscIdAsc(UUID playlistId);

  // 여러 플레이리스트의 콘텐츠 매핑 일괄 조회 (추가된 순서 고정, 동률 시 id로 타이브레이크)
  List<PlaylistContent> findByPlaylistIdInOrderByCreatedAtAscIdAsc(List<UUID> playlistIds);

  boolean existsByPlaylistIdAndContentId(UUID playlistId, UUID contentId);

  void deleteByPlaylistIdAndContentId(UUID playlistId, UUID contentId);
}
