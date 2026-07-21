package com.team02.mopl.domain.auth.oauth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

import com.team02.mopl.domain.auth.oauth.provider.GoogleOidcProvider;
import com.team02.mopl.domain.auth.oauth.provider.KakaoOidcProvider;
import com.team02.mopl.domain.auth.oauth.provider.MoplOidcProvider;
import com.team02.mopl.domain.auth.oauth.provider.MoplOidcProviderRegistry;
import com.team02.mopl.domain.auth.oauth.provider.OAuth2UserInfo;
import com.team02.mopl.domain.user.service.UserService;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MoplOidcUserServiceTest {

  @Mock private MoplOidcProviderRegistry providerRegistry;
  @Mock private UserService userService;
  @Mock private OAuth2UserService<OidcUserRequest, OidcUser> delegate;

  @ParameterizedTest
  @ValueSource(strings = {"google", "kakao"})
  @DisplayName("지원하는 제공자일때 소셜사용자를 등록하고 oidcUser를 반환한다")
  void success_shouldRegisterSocialAccountAndReturnOidcUser_whenProviderIsSupported(
      String registrationId) {
    // given
    MoplOidcUserService oidcUserService = new MoplOidcUserService(providerRegistry, userService);
    ReflectionTestUtils.setField(oidcUserService, "delegate", delegate);

    OidcUserRequest mockRequest = mock(OidcUserRequest.class);
    ClientRegistration mockClientRegistration = mock(ClientRegistration.class);
    given(mockRequest.getClientRegistration()).willReturn(mockClientRegistration);
    given(mockClientRegistration.getRegistrationId()).willReturn(registrationId);

    OidcUser mockOidcUser = mock(OidcUser.class);
    given(delegate.loadUser(mockRequest)).willReturn(mockOidcUser);

    MoplOidcProvider provider =
        switch (registrationId) {
          case "google" -> new GoogleOidcProvider();
          case "kakao" -> new KakaoOidcProvider();
          default -> throw new IllegalStateException("Unexpected value: " + registrationId);
        };
    given(providerRegistry.get(registrationId)).willReturn(provider);

    Map<String, Object> attributes = new HashMap<>();
    attributes.put("sub", "1234");
    attributes.put("name", "name!");
    attributes.put("nickname", "name!");
    attributes.put("email", "example@gmail.com");
    attributes.put("picture", "URL");
    given(mockOidcUser.getAttributes()).willReturn(attributes);
    OAuth2UserInfo userInfo = provider.getOAuth2UserInfo(attributes);

    willDoNothing().given(userService).registerSocialUser(userInfo);

    // when
    OidcUser actual = oidcUserService.loadUser(mockRequest);

    // then
    assertThat(actual).isEqualTo(mockOidcUser);
    then(userService).should(times(1)).registerSocialUser(eq(userInfo));
  }
}
