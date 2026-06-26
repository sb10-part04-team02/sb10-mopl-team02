package com.team02.mopl.domain.watching.entity;

import com.team02.mopl.domain.content.entity.Content;
import com.team02.mopl.domain.user.entity.User;
import com.team02.mopl.global.entity.BaseMutableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "watching_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WatchingSession extends BaseMutableEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "content_id", nullable = false, updatable = false)
  private Content content;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false, updatable = false)
  private User user;

  @Column(name = "joined_at", nullable = false)
  private Instant joinedAt;

  @Column(name = "exited_at")
  private Instant exitedAt;

  public WatchingSession(Content content, User user, Instant joinedAt, Instant exitedAt) {
    this.content = content;
    this.user = user;
    this.joinedAt = joinedAt;
    this.exitedAt = exitedAt;
  }

  public void exit() {
    this.exitedAt = Instant.now();
  }
}
