package com.team02.mopl.domain.auth.oauth.service;

import com.team02.mopl.domain.auth.oauth.provider.MoplOidcProvider;
import com.team02.mopl.domain.auth.oauth.provider.MoplOidcProviderRegistry;
import com.team02.mopl.domain.auth.oauth.provider.OAuth2UserInfo;
import com.team02.mopl.domain.user.service.UserService;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

@Component
public class MoplOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

  private final MoplOidcProviderRegistry providerRegistry;
  private final UserService userService;
  private final OAuth2UserService<OidcUserRequest, OidcUser> delegate;

  public MoplOidcUserService(MoplOidcProviderRegistry providerRegistry, UserService userService) {
    this.providerRegistry = providerRegistry;
    this.userService = userService;
    this.delegate = new OidcUserService(); // 기본 시큐리티 서비스 사용
  }

  @Override
  public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
    String registrationId = userRequest.getClientRegistration().getRegistrationId();
    MoplOidcProvider provider = providerRegistry.get(registrationId);

    // 인증서버에 요청해서 oidcUser 정보 가져오기
    OidcUser oidcUser = delegate.loadUser(userRequest);

    // 맞는 provider에서 user정보 가져오기
    OAuth2UserInfo userInfo = provider.getOAuth2UserInfo(oidcUser.getAttributes());

    // 유저등록
    userService.registerSocialUser(userInfo);

    return oidcUser;
  }
}
