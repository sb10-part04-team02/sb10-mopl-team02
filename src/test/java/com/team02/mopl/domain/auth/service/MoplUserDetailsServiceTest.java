package com.team02.mopl.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.team02.mopl.domain.auth.entity.MoplUserDetails;
import com.team02.mopl.domain.user.dto.UserDto;
import com.team02.mopl.domain.user.entity.User;
import com.team02.mopl.domain.user.entity.enums.Role;
import com.team02.mopl.domain.user.mapper.UserMapper;
import com.team02.mopl.domain.user.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
class MoplUserDetailsServiceTest {

  private static String email;
  private static String encryptedPassword;

  @Mock private UserRepository userRepository;
  @Mock private UserMapper userMapper;
  @InjectMocks private MoplUserDetailsService userDetailsService;

  @BeforeAll
  static void setUp() {
    email = "example@gmail.com";
    encryptedPassword = "encryptedPassword";
  }

  @Test
  @DisplayName("email을 가진 유저가 없다면 UsernameNotFoundException을 반환한다")
  void fail_shouldThrowUsernameNotFoundException_whenUserDoesNotExistWithEmail() {
    // given
    given(userRepository.findByEmailAndDeletedAtIsNull(anyString())).willReturn(Optional.empty());

    // when & then
    assertThrows(
        UsernameNotFoundException.class, () -> userDetailsService.loadUserByUsername(email));
  }

  @Test
  @DisplayName("email을 가진 유저가 있다면 MoplUserDetails를 반환한다")
  void success_shouldReturnMoplUserDetails_whenUserHasTheEmail() {
    // given
    User mockUser = mock(User.class);
    UserDto userDto =
        new UserDto(UUID.randomUUID(), Instant.now(), email, "이름", null, Role.USER, false);

    given(mockUser.getPassword()).willReturn(encryptedPassword);
    given(userRepository.findByEmailAndDeletedAtIsNull(anyString()))
        .willReturn(Optional.of(mockUser));
    given(userMapper.toDto(any(User.class))).willReturn(userDto);

    // when
    UserDetails actual = userDetailsService.loadUserByUsername(email);

    // then
    assertThat(actual)
        .isInstanceOf(MoplUserDetails.class)
        .asInstanceOf(InstanceOfAssertFactories.type(MoplUserDetails.class))
        .extracting("userDto", "password")
        .containsExactly(userDto, encryptedPassword);
  }
}
