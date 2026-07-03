package com.team02.mopl.domain.playlist.repository;

import com.team02.mopl.domain.playlist.entity.Playlist;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaylistRepository
    extends JpaRepository<Playlist, UUID>, PlaylistRepositoryCustom {

  List<Playlist> findByOwnerIdAndDeletedAtIsNull(UUID ownerId);

  Optional<Playlist> findByIdAndDeletedAtIsNull(UUID id);
}
