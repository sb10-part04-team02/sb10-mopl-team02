package com.team02.mopl.domain.watching.websocket;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 시청 세션 구독 추적 레지스트리.
 *
 * <p>STOMP UNSUBSCRIBE 프레임에는 destination이 없어 subscriptionId만으로는 어떤 시청 세션을 종료해야 할지 알 수 없다. 구독 시점에
 * (wsSessionId, subscriptionId) → (watchingSessionId, userId) 매핑을 기록해 두고, UNSUBSCRIBE/DISCONNECT 시
 * 조회한다.
 */
@Component
public class WatchingSubscriptionRegistry {

  // 구독 시점의 시청 세션과 소유자. userId는 이탈 시 소유자 검증에 쓰인다.
  public record WatchingSubscription(UUID watchingSessionId, UUID userId) {}

  private final ConcurrentHashMap<String, Map<String, WatchingSubscription>> sessionSubscriptions =
      new ConcurrentHashMap<>();

  public void register(String wsSessionId, String subscriptionId, WatchingSubscription value) {
    sessionSubscriptions
        .computeIfAbsent(wsSessionId, key -> new ConcurrentHashMap<>())
        .put(subscriptionId, value);
  }

  public Optional<WatchingSubscription> remove(String wsSessionId, String subscriptionId) {
    Map<String, WatchingSubscription> subscriptions = sessionSubscriptions.get(wsSessionId);
    if (subscriptions == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(subscriptions.remove(subscriptionId));
  }

  public List<WatchingSubscription> removeAll(String wsSessionId) {
    Map<String, WatchingSubscription> subscriptions = sessionSubscriptions.remove(wsSessionId);
    return subscriptions == null ? List.of() : List.copyOf(subscriptions.values());
  }
}
