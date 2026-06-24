package com.team02.mopl.domain.playlist.repository;

import com.team02.mopl.domain.playlist.entity.PlaylistContent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaylistContentRepository extends JpaRepository<PlaylistContent, UUID> {

  List<PlaylistContent> findByPlaylistId(UUID playlistId);

  boolean existsByPlaylistIdAndContentId(UUID playlistId, UUID contentId);

  void deleteByPlaylistIdAndContentId(UUID playlistId, UUID contentId);
}
