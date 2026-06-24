package com.team02.mopl.domain.dm.repository;

import com.team02.mopl.domain.dm.entity.Conversation;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {}
