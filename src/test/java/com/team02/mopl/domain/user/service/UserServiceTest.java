package com.team02.mopl.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.team02.mopl.domain.user.dto.UserCreateRequest;
import com.team02.mopl.domain.user.dto.UserDto;
import com.team02.mopl.domain.user.entity.User;
import com.team02.mopl.domain.user.entity.enums.Role;
import com.team02.mopl.domain.user.exception.UserEmailDuplicateException;
import com.team02.mopl.domain.user.mapper.UserMapper;
import com.team02.mopl.domain.user.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  @Mock UserRepository userRepository;

  @Mock UserMapper userMapper;

  @InjectMocks UserService userService;

  @Captor ArgumentCaptor<User> userCaptor;

  @Spy private PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

  @Nested
  class CreateUser {
    private final String name = "username";
    private final String email = "example@gmail.com";
    private final String password = "12345678";

    @Test
    @DisplayName("이메일이 중복일 때 409 에러를 반환한다")
    void fail_shouldReturn409_whenEmailIsDuplicate() {
      // given
      UserCreateRequest request = new UserCreateRequest(name, email, password);
      given(userRepository.existsByEmail(anyString())).willReturn(true);

      // when & then
      assertThrows(UserEmailDuplicateException.class, () -> userService.createUser(request));
    }

    @Test
    @DisplayName("정상적인 회원가입 요청일 때 UserDto를 반환한다")
    void success_shouldReturnUserDto_whenRequestIsValid() {
      // given
      UserCreateRequest request = new UserCreateRequest(name, email, password);
      User mockUser = new User(name, email, "encryptedPassword", null, Role.USER, false);
      UserDto expect =
          new UserDto(UUID.randomUUID(), Instant.now(), email, name, null, Role.USER, false);
      given(userRepository.existsByEmail(anyString())).willReturn(false);
      given(userRepository.save(any(User.class))).willReturn(mockUser);
      given(userMapper.toDto(any(User.class))).willReturn(expect);

      // when
      userService.createUser(request);

      // then
      then(userRepository).should().existsByEmail(anyString());
      then(userRepository).should().save(userCaptor.capture());
      then(userMapper).should().toDto(any(User.class));

      User savedUser = userCaptor.getValue();
      assertThat(savedUser.getPassword())
          .isNotEqualTo(request.password()); // 암호화된 패스워드와 평문 패스워드 다름 비교
    }
  }
}
