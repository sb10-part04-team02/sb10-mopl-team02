package com.team02.mopl.domain.auth.oauth.provider;

import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class GoogleOidcProvider implements MoplOidcProvider {

  @Override
  public OAuthType supports() {
    return OAuthType.GOOGLE;
  }

  @Override
  public OAuth2UserInfo getOAuth2UserInfo(Map<String, Object> attributes) {
    String socialUserId = (String) attributes.get("sub");
    String name = (String) attributes.get("name");
    String email = (String) attributes.get("email");
    String profileImageUrl = (String) attributes.get("picture");

    return new OAuth2UserInfo(supports(), socialUserId, name, email, profileImageUrl);
  }
}
