package com.team02.mopl.domain.auth.oauth.provider;

import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class KakaoOidcProvider implements MoplOidcProvider {

  @Override
  public OAuthType supports() {
    return OAuthType.KAKAO;
  }

  @Override
  public OAuth2UserInfo getOAuth2UserInfo(Map<String, Object> attributes) {
    String socialUserId = (String) attributes.get("sub");
    String name = (String) attributes.get("nickname");
    String profileImageUrl = (String) attributes.get("picture");
    // API제약으로 가상의 이메일 {name}_{id}@kakao.com로 진행
    String email = name + "_" + socialUserId + "@kakao.com";

    return new OAuth2UserInfo(supports(), socialUserId, name, email, profileImageUrl);
  }
}
