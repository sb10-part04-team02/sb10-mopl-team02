package com.team02.mopl.domain.user.repository;

import com.team02.mopl.domain.user.entity.User;
import com.team02.mopl.domain.user.entity.enums.Role;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID>, UserRepositoryCustom {

  boolean existsByEmailAndDeletedAtIsNull(String email);

  Optional<User> findByIdAndDeletedAtIsNull(UUID id);

  Optional<User> findByEmailAndDeletedAtIsNull(String email);

  @Query(
      """
  SELECT COUNT(u) FROM User u
   WHERE u.deletedAt IS NULL
     AND (:email IS NULL OR u.email LIKE %:email%)
     AND (:role IS NULL OR u.role = :role)
     AND (:isLocked IS NULL OR u.isLocked = :isLocked)
  """)
  long countUsersByCursor(
      @Param("email") String email, @Param("role") Role role, @Param("isLocked") Boolean isLocked);
}
