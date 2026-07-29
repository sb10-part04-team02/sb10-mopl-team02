package com.team02.mopl.domain.user.repository;

import com.team02.mopl.domain.auth.oauth.provider.OAuthType;
import com.team02.mopl.domain.user.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, UUID>, UserRepositoryCustom {

  boolean existsByEmailAndDeletedAtIsNull(String email);

  Optional<User> findByIdAndDeletedAtIsNull(UUID id);

  Optional<User> findByEmailAndDeletedAtIsNull(String email);

  @Query(
      """
      SELECT u FROM User u
        JOIN SocialAccount sa ON sa.userId = u.id
       WHERE sa.provider = :provider
         AND sa.providerUserId = :subject
         AND sa.deletedAt IS NULL
         AND u.deletedAt IS NULL
      """)
  Optional<User> findBySubjectAndProviderAndDeletedAtIsNull(OAuthType provider, String subject);
}
