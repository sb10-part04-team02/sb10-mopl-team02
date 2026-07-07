package com.team02.mopl.domain.user.event;

import com.team02.mopl.domain.user.entity.enums.Role;
import java.util.UUID;

public record RoleUpdatedEvent(UUID userId, Role oldRole, Role newRole) {}
