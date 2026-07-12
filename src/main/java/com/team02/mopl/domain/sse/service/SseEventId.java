package com.team02.mopl.domain.sse.service;

import java.util.Optional;
import java.util.UUID;

public final class SseEventId {

  private static final String CONNECT_PREFIX = "connect:";
  private static final String NOTIFICATION_PREFIX = "notification:";
  private static final String DIRECT_MESSAGE_PREFIX = "direct-message:";

  private SseEventId() {}

  public static String connect(UUID id) {
    return CONNECT_PREFIX + id;
  }

  public static String notification(String id) {
    return NOTIFICATION_PREFIX + id;
  }

  public static String directMessage(String id) {
    return DIRECT_MESSAGE_PREFIX + id;
  }

  public static String fromEventName(String eventName, String eventId) {
    if (eventId == null || eventId.contains(":")) {
      return eventId;
    }

    return switch (eventName) {
      case "notifications" -> notification(eventId);
      case "direct-messages" -> directMessage(eventId);
      default -> eventId;
    };
  }

  public static Optional<UUID> parseNotificationId(String lastEventId) {
    if (lastEventId == null || lastEventId.isBlank()) {
      return Optional.empty();
    }

    if (!lastEventId.startsWith(NOTIFICATION_PREFIX)) {
      return Optional.empty();
    }

    String rawId = lastEventId.substring(NOTIFICATION_PREFIX.length());
    return Optional.of(UUID.fromString(rawId));
  }
}
