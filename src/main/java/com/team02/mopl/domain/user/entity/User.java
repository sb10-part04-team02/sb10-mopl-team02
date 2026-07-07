package com.team02.mopl.domain.user.entity;

import com.team02.mopl.domain.user.entity.enums.Role;
import com.team02.mopl.global.entity.BaseMutableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseMutableEntity {

  @Column(nullable = false)
  private String name;

  @Column(nullable = false, unique = true)
  private String email;

  @Column(nullable = true)
  private String password;

  @Column(nullable = true)
  private String profileImageUrl;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Role role;

  @Column(nullable = false)
  private boolean isLocked;

  public User(
      String name,
      String email,
      String password,
      String profileImageUrl,
      Role role,
      boolean isLocked) {
    this.name = Objects.requireNonNull(name, "name은 null일 수 없습니다.");
    this.email = Objects.requireNonNull(email, "email은 null일 수 없습니다.");
    this.password = password;
    this.profileImageUrl = profileImageUrl;
    this.role = role == null ? Role.USER : role;
    this.isLocked = isLocked;
  }

  public void updateProfile(String name, String profileImageUrl) {
    this.name = Objects.requireNonNull(name, "name은 null일 수 없습니다.");
    this.profileImageUrl = profileImageUrl;
  }

  public Role updateRole(Role newRole) {
    Role oldRole = this.role;
    this.role = newRole;
    return oldRole;
  }
}
