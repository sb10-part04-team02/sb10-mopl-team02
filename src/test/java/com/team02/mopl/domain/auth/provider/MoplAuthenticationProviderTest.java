package com.team02.mopl.domain.auth.provider;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

import com.team02.mopl.domain.auth.entity.MoplUserDetails;
import com.team02.mopl.domain.auth.jwt.JwtRegistry;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class MoplAuthenticationProviderTest {

  @Mock private UserDetailsService userDetailsService;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private JwtRegistry jwtRegistry;
  @InjectMocks private MoplAuthenticationProvider authenticationProvider;

  @Nested
  class RetrieveUser {
    @Test
    @DisplayName("인증 필수항목이 들어오면 유저정보를 조회해서 반환한다")
    void success_shouldReturnUserDetails_whenUserExistsWithGivenUsername() {
      // given
      String email = "example@gmail.com";
      MoplUserDetails mockUserDetails = mock(MoplUserDetails.class);
      UsernamePasswordAuthenticationToken mockAuthToken =
          mock(UsernamePasswordAuthenticationToken.class);
      given(userDetailsService.loadUserByUsername(eq(email))).willReturn(mockUserDetails);

      // when & then
      assertDoesNotThrow(() -> authenticationProvider.retrieveUser(email, mockAuthToken));
      then(userDetailsService).should(times(1)).loadUserByUsername(eq(email));
    }

    @Test
    @DisplayName("유저를 찾을수없는 예외가 터지면 BadCredentialsException으로 감싸서 던진다")
    void fail_shouldThrowBadCredentials_whenUserNotFoundExceptionOccurs() {
      // given
      String email = "notfound@gmail.com";
      UsernamePasswordAuthenticationToken mockAuthToken =
          mock(UsernamePasswordAuthenticationToken.class);
      given(userDetailsService.loadUserByUsername(anyString()))
          .willThrow(UsernameNotFoundException.class);

      // when & then
      assertThrows(
          BadCredentialsException.class,
          () -> authenticationProvider.retrieveUser(email, mockAuthToken));
    }
  }

  @Nested
  class AdditionalAuthenticationChecks {

    private MoplUserDetails mockUserDetails;
    private UserDto mockUserDto;
    private UUID userId;

    @BeforeEach
    void setUp() {
      mockUserDetails = mock(MoplUserDetails.class);
      mockUserDto = mock(UserDto.class);
      userId = UUID.randomUUID();

      given(mockUserDetails.getUserDto()).willReturn(mockUserDto);
      given(mockUserDto.id()).willReturn(userId);
    }

    @Test
    @DisplayName("입력된 비밀번호가 임시비밀번호와 일치하면 임시비밀번호를 삭제하고 통과한다")
    void success_shouldDeleteTempPasswordAndReturn_whenInputMatchesTempPassword() {
      // given
      String tempPassword = "tempPassword";
      given(jwtRegistry.getTempPassword(userId)).willReturn(tempPassword);

      UsernamePasswordAuthenticationToken auth = mock(UsernamePasswordAuthenticationToken.class);
      given(auth.getCredentials()).willReturn(tempPassword);

      // when
      authenticationProvider.additionalAuthenticationChecks(mockUserDetails, auth);

      // then
      then(jwtRegistry).should(times(1)).deleteTempPassword(userId);
    }

    @Test
    @DisplayName("authToken에 credential이 없으면 예외를 던진다")
    void fail_shouldThrowException_whenCredentialsIsNull() {
      // given
      UsernamePasswordAuthenticationToken mockAuth =
          mock(UsernamePasswordAuthenticationToken.class);
      given(mockAuth.getCredentials()).willReturn(null);

      // when & then
      assertThrows(
          BadCredentialsException.class,
          () -> authenticationProvider.additionalAuthenticationChecks(mockUserDetails, mockAuth));
    }

    @Test
    @DisplayName("입력된 비밀번호가 임시비밀번호와 불일치하면 예외를 던진다")
    void fail_shouldThrowException_whenInputDoesNotMatchTempPassword() {
      // given
      String tempPassword = "tempPassword";
      given(jwtRegistry.getTempPassword(userId)).willReturn(tempPassword);

      UsernamePasswordAuthenticationToken mockAuth =
          mock(UsernamePasswordAuthenticationToken.class);
      given(mockAuth.getCredentials()).willReturn("invalidInputPassword");

      // when & then
      assertThrows(
          BadCredentialsException.class,
          () -> authenticationProvider.additionalAuthenticationChecks(mockUserDetails, mockAuth));
    }

    @Test
    @DisplayName("입력된 비밀번호가 암호화된 비밀번호와 일치하면 통과한다")
    void success_shouldReturnSuccessfully_whenPlainPasswordMatchesEncodedPassword() {
      // given
      given(jwtRegistry.getTempPassword(userId)).willReturn(null);

      String inputPassword = "inputPassword";
      String encodedPassword = "encodedPassword";

      UsernamePasswordAuthenticationToken mockAuth =
          mock(UsernamePasswordAuthenticationToken.class);
      given(mockAuth.getCredentials()).willReturn(inputPassword);
      given(mockUserDetails.getPassword()).willReturn(encodedPassword);
      given(passwordEncoder.matches(inputPassword, encodedPassword)).willReturn(true);

      // when & then
      assertDoesNotThrow(
          () -> authenticationProvider.additionalAuthenticationChecks(mockUserDetails, mockAuth));
    }

    @Test
    @DisplayName("입력된 비밀번호가 암호화된 비밀번호와 불일치하면 예외를 던진다")
    void fail_shouldThrowException_whenPlainPasswordDoesNotMatchEncodedPassword() {
      // given
      given(jwtRegistry.getTempPassword(userId)).willReturn(null);

      String inputPassword = "invalidInputPassword";
      String encodedPassword = "encodedPassword";

      UsernamePasswordAuthenticationToken mockAuth =
          mock(UsernamePasswordAuthenticationToken.class);
      given(mockAuth.getCredentials()).willReturn(inputPassword);
      given(mockUserDetails.getPassword()).willReturn(encodedPassword);
      given(passwordEncoder.matches(inputPassword, encodedPassword)).willReturn(false);

      // when & then
      assertThrows(
          BadCredentialsException.class,
          () -> authenticationProvider.additionalAuthenticationChecks(mockUserDetails, mockAuth));
    }
  }
}
