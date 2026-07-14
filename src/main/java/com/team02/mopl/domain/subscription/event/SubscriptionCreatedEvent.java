package com.team02.mopl.domain.subscription.event;

import java.util.UUID;

public record SubscriptionCreatedEvent(
    UUID subscriptionId,
    UUID subscriberId,
    String subscriberName,
    UUID playlistOwnerId,
    String playlistTitle) {}
