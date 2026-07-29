package com.team02.mopl.domain.subscription.entity;

import com.team02.mopl.domain.playlist.entity.Playlist;
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
@Table(name = "playlist_subscriptions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Subscription extends BaseEntity {

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "playlist_id", nullable = false, updatable = false)
  private Playlist playlist;

  public Subscription(UUID userId, Playlist playlist) {
    this.userId = Objects.requireNonNull(userId, "userId는 null일 수 없습니다.");
    this.playlist = Objects.requireNonNull(playlist, "playlist는 null일 수 없습니다.");
  }
}
