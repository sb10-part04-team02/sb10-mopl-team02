package com.team02.mopl.domain.playlist.entity;

import com.team02.mopl.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "playlist_contents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaylistContent extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "playlist_id", nullable = false, updatable = false)
  private Playlist playlist;

  @Column(name = "content_id", nullable = false, updatable = false)
  private UUID contentId;

  public PlaylistContent(Playlist playlist, UUID contentId) {
    this.playlist = Objects.requireNonNull(playlist, "playlist는 null일 수 없습니다.");
    this.contentId = Objects.requireNonNull(contentId, "contentId는 null일 수 없습니다.");
  }
}
