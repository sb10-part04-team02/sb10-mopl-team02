package com.team02.mopl.domain.user.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.team02.mopl.domain.user.entity.enums.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class UserTest {

  private User newUser() {
    return new User("원본이름", "user@example.com", "pw", "http://img/old.png", Role.USER, false);
  }

  @Nested
  class UpdateProfile {

    @Test
    @DisplayName("name과 profileImageUrl이 주어지면 둘 다 변경된다")
    void updateProfile_bothFields() {
      // given
      User user = newUser();

      // when
      user.updateProfile("수정이름", "http://img/new.png");

      // then
      assertThat(user.getName()).isEqualTo("수정이름");
      assertThat(user.getProfileImageUrl()).isEqualTo("http://img/new.png");
    }

    @ParameterizedTest
    @DisplayName("name이 null이거나 빈 문자열, 공백이면 기존 이름을 유지한다")
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "\n"})
    void updateProfile_keepsNameWhenBlank(String blankName) {
      // given
      User user = newUser();

      // when
      user.updateProfile(blankName, "http://img/new.png");

      // then
      assertThat(user.getName()).isEqualTo("원본이름");
      assertThat(user.getProfileImageUrl()).isEqualTo("http://img/new.png");
    }

    @Test
    @DisplayName("profileImageUrl은 null이 그대로 대입되어 이미지 제거 용도로 쓰일 수 있다")
    void updateProfile_allowsNullProfileImageUrl() {
      // given
      User user = newUser();

      // when
      user.updateProfile("수정이름", null);

      // then
      assertThat(user.getName()).isEqualTo("수정이름");
      assertThat(user.getProfileImageUrl()).isNull();
    }
  }
}
