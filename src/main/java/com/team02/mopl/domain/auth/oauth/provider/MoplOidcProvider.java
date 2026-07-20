package com.team02.mopl.domain.auth.oauth.provider;

import java.util.Map;

public interface MoplOidcProvider {
  OAuthType supports();

  OAuth2UserInfo getOAuth2UserInfo(Map<String, Object> attributes);
}
