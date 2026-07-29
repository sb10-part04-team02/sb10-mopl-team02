package com.team02.mopl.global.websocket;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class WebSocketSessionRegistry {

  private final ConcurrentHashMap<String, UUID> sessionUserMap = new ConcurrentHashMap<>();

  public void register(String stompSessionId, UUID userId) {
    sessionUserMap.put(stompSessionId, userId);
  }

  public Optional<UUID> getUserId(String stompSessionId) {
    return Optional.ofNullable(sessionUserMap.get(stompSessionId));
  }

  public void remove(String stompSessionId) {
    sessionUserMap.remove(stompSessionId);
  }
}
