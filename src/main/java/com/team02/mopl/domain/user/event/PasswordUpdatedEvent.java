package com.team02.mopl.domain.user.event;

import java.util.UUID;

public record PasswordUpdatedEvent(UUID userId) {}
