package com.team02.mopl.domain.auth.login.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

import com.team02.mopl.domain.auth.entity.MoplUserDetails;
import com.team02.mopl.domain.auth.jwt.JwtRegistry;
import com.team02.mopl.domain.auth.login.token.MoplAuthenticationToken;
import com.team02.mopl.domain.user.dto.UserDto;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class MoplAuthenticationProviderTest {

  @Mock private UserDetailsService userDetailsService;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private JwtRegistry jwtRegistry;
  @InjectMocks private MoplAuthenticationProvider authProvider;

  private String email;
  private String password;

  @BeforeEach
  void setUp() {
    email = "example@gmail.com";
    password = "password";
  }

  @Nested
  class Authenticate {
    @Test
    @DisplayName("입력된 비밀번호가 암호화된 비밀번호와 일치하면 authentication을 반환한다")
    void success_shouldReturnAuthentication_whenPlainPasswordMatchesEncodedPassword() {
      // given
      Authentication auth = new MoplAuthenticationToken(email, password);

      MoplUserDetails mockUserDetails = mock(MoplUserDetails.class);
      given(userDetailsService.loadUserByUsername(anyString())).willReturn(mockUserDetails);

      UserDto mockUserDto = mock(UserDto.class);
      given(mockUserDetails.getUserDto()).willReturn(mockUserDto);

      UUID userId = UUID.randomUUID();
      given(mockUserDto.id()).willReturn(userId);

      // 일반패스워드 검사 통과
      given(mockUserDetails.getPassword()).willReturn("password");
      given(passwordEncoder.matches(anyString(), anyString())).willReturn(true);

      // when
      Authentication result = authProvider.authenticate(auth);

      // then
      assertThat(result.getPrincipal()).isEqualTo(mockUserDetails);
    }

    @Test
    @DisplayName("입력된 비밀번호가 임시비밀번호와 일치하면 임시비밀번호를 삭제하고 authentication을 반환한다")
    void success_shouldDeleteTempPasswordAndReturnAuthentication_whenInputMatchesTempPassword() {
      // given
      Authentication auth = new MoplAuthenticationToken(email, password);
      MoplUserDetails mockUserDetails = mock(MoplUserDetails.class);
      given(userDetailsService.loadUserByUsername(anyString())).willReturn(mockUserDetails);

      UserDto mockUserDto = mock(UserDto.class);
      given(mockUserDetails.getUserDto()).willReturn(mockUserDto);

      UUID userId = UUID.randomUUID();
      given(mockUserDto.id()).willReturn(userId);

      String tempPassword = password;
      given(jwtRegistry.getTempPassword(userId)).willReturn(tempPassword);

      // when
      Authentication result = authProvider.authenticate(auth);

      // then
      then(jwtRegistry).should(times(1)).deleteTempPassword(eq(userId));
      assertThat(result.getPrincipal()).isEqualTo(mockUserDetails);
    }

    @Test
    @DisplayName("유저를 찾을수 없으면 예외를 던진다")
    void fail_shouldThrowException_whenUserNotFound() {
      // given
      Authentication auth = new MoplAuthenticationToken(email, password);

      given(userDetailsService.loadUserByUsername(anyString()))
          .willThrow(UsernameNotFoundException.class);

      // when & then
      assertThrows(BadCredentialsException.class, () -> authProvider.authenticate(auth));
    }

    @Test
    @DisplayName("입력된 비밀번호가 임시비밀번호와 불일치하면 예외를 던진다")
    void fail_shouldThrowException_whenInputDoesNotMatchTempPassword() {
      // given
      Authentication auth = new MoplAuthenticationToken(email, password);
      MoplUserDetails mockUserDetails = mock(MoplUserDetails.class);
      given(userDetailsService.loadUserByUsername(anyString())).willReturn(mockUserDetails);

      UserDto mockUserDto = mock(UserDto.class);
      given(mockUserDetails.getUserDto()).willReturn(mockUserDto);

      UUID userId = UUID.randomUUID();
      given(mockUserDto.id()).willReturn(userId);

      String tempPassword = "tempPassword";
      given(jwtRegistry.getTempPassword(userId)).willReturn(tempPassword);

      // when & then
      assertThrows(BadCredentialsException.class, () -> authProvider.authenticate(auth));
    }

    @Test
    @DisplayName("입력된 비밀번호가 암호화된 비밀번호와 불일치하면 예외를 던진다")
    void fail_shouldThrowException_whenPlainPasswordDoesNotMatchEncodedPassword() {
      // given
      Authentication auth = new MoplAuthenticationToken(email, password);

      MoplUserDetails mockUserDetails = mock(MoplUserDetails.class);
      given(userDetailsService.loadUserByUsername(anyString())).willReturn(mockUserDetails);

      UserDto mockUserDto = mock(UserDto.class);
      given(mockUserDetails.getUserDto()).willReturn(mockUserDto);

      UUID userId = UUID.randomUUID();
      given(mockUserDto.id()).willReturn(userId);

      // 일반패스워드 검사 통과
      given(mockUserDetails.getPassword()).willReturn("encodedPassword");
      given(passwordEncoder.matches(anyString(), anyString())).willReturn(false);

      // when & then
      assertThrows(BadCredentialsException.class, () -> authProvider.authenticate(auth));
    }
  }

  @Nested
  class Supports {
    @Test
    @DisplayName("MoplAuthenticationToken 클래스가 들어오면 true를 반환한다")
    void success_shouldReturnTrue_whenMoplAuthenticationTokenIsProvided() {
      // when
      boolean result = authProvider.supports(MoplAuthenticationToken.class);

      // then
      assertThat(result).isTrue();
    }
  }
}
