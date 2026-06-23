package com.team02.mopl.domain.notification.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository
    extends JpaRepository<com.team02.mopl.notification.entity.Notification, UUID> {}
