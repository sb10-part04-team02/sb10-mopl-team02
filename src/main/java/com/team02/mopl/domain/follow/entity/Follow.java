package com.team02.mopl.domain.follow.entity;

import com.team02.mopl.domain.user.entity.User;
import com.team02.mopl.global.entity.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "follows")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Follow extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "follower_id", nullable = false)
  private User follower;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "followee_id", nullable = false)
  private User followee;

  public Follow(User follower, User followee) {
    this.follower = Objects.requireNonNull(follower, "follower는 null일 수 없습니다.");
    this.followee = Objects.requireNonNull(followee, "followee는 null일 수 없습니다.");
  }
}
