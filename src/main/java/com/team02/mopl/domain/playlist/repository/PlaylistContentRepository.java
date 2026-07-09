package com.team02.mopl.domain.playlist.repository;

import com.team02.mopl.domain.playlist.entity.PlaylistContent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaylistContentRepository extends JpaRepository<PlaylistContent, UUID> {

  // 플레이리스트에 추가된 순서(createdAt)대로 반환해 콘텐츠 노출 순서를 고정 (동률 시 id로 타이브레이크)
  List<PlaylistContent> findByPlaylistIdOrderByCreatedAtAscIdAsc(UUID playlistId);

  // 여러 플레이리스트의 콘텐츠 매핑 일괄 조회 (추가된 순서 고정, 동률 시 id로 타이브레이크)
  List<PlaylistContent> findByPlaylistIdInOrderByCreatedAtAscIdAsc(List<UUID> playlistIds);

  boolean existsByPlaylistIdAndContentId(UUID playlistId, UUID contentId);

  // 매핑을 벌크 삭제하고 영향 행 수를 반환. 0이면 미포함(멱등 삭제 판정용)
  @Modifying(clearAutomatically = true)
  @Query(
      "DELETE FROM PlaylistContent pc "
          + "WHERE pc.playlist.id = :playlistId AND pc.contentId = :contentId")
  int deleteByPlaylistIdAndContentId(
      @Param("playlistId") UUID playlistId, @Param("contentId") UUID contentId);
}
