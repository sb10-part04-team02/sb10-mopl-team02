package com.team02.mopl.domain.auth.oauth.provider;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toUnmodifiableMap;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class MoplOidcProviderRegistry {

  private final Map<OAuthType, MoplOidcProvider> providerMap;

  public MoplOidcProviderRegistry(List<MoplOidcProvider> providers) {
    this.providerMap =
        providers.stream().collect(toUnmodifiableMap(MoplOidcProvider::supports, identity()));
  }

  public MoplOidcProvider get(String registrationId) {
    return Optional.ofNullable(providerMap.get(OAuthType.of(registrationId)))
        .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 OAuthType: " + registrationId));
  }
}
