package com.team02.mopl.domain.follow.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.team02.mopl.domain.user.entity.User;
import com.team02.mopl.domain.user.entity.enums.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FollowTest {

  @Test
  @DisplayName("팔로우를 생성하면 팔로워와 팔로우 대상이 초기화된다")
  void constructor_initializesFields() {
    User follower = createUser("팔로워", "follower@test.com");
    User followee = createUser("팔로우 대상", "followee@test.com");

    Follow follow = new Follow(follower, followee);

    assertThat(follow.getFollower()).isSameAs(follower);
    assertThat(follow.getFollowee()).isSameAs(followee);
  }

  @Test
  @DisplayName("팔로워가 null이면 예외가 발생한다")
  void constructor_whenFollowerIsNull_throwsException() {
    User followee = createUser("팔로우 대상", "followee@test.com");

    assertThatNullPointerException().isThrownBy(() -> new Follow(null, followee));
  }

  @Test
  @DisplayName("팔로우 대상이 null이면 예외가 발생한다")
  void constructor_whenFolloweeIsNull_throwsException() {
    User follower = createUser("팔로워", "follower@test.com");

    assertThatNullPointerException().isThrownBy(() -> new Follow(follower, null));
  }

  @Test
  @DisplayName("팔로우를 삭제하면 deletedAt이 설정된다")
  void delete_setsDeletedAt() {
    User follower = createUser("팔로워", "follower@test.com");
    User followee = createUser("팔로우 대상", "followee@test.com");
    Follow follow = new Follow(follower, followee);

    follow.delete();

    assertThat(follow.isDeleted()).isTrue();
    assertThat(follow.getDeletedAt()).isNotNull();
  }

  private User createUser(String name, String email) {
    return new User(name, email, "password", null, Role.USER, false);
  }
}
