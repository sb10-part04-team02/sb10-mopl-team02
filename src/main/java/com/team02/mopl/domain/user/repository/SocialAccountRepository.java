package com.team02.mopl.domain.user.repository;

import com.team02.mopl.domain.auth.oauth.provider.OAuthType;
import com.team02.mopl.domain.user.entity.SocialAccount;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, UUID> {

  boolean existsByUserIdAndProvider(UUID userId, OAuthType provider);
}
