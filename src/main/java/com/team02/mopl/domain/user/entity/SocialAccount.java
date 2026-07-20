package com.team02.mopl.domain.user.entity;

import com.team02.mopl.domain.auth.oauth.provider.OAuthType;
import com.team02.mopl.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "social_accounts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SocialAccount extends BaseEntity {

  @Column(nullable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private OAuthType provider;

  @Column(nullable = false, length = 100)
  private String providerUserId;

  public SocialAccount(UUID userId, OAuthType provider, String providerUserId) {
    this.userId = Objects.requireNonNull(userId, "userId는 null일 수 없습니다.");
    this.provider = Objects.requireNonNull(provider, "provider는 null일 수 없습니다.");
    this.providerUserId = Objects.requireNonNull(providerUserId, "providerUserId는 null일 수 없습니다");
  }
}
