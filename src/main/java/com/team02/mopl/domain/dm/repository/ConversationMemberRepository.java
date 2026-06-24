package com.team02.mopl.domain.dm.repository;

import com.team02.mopl.domain.dm.entity.ConversationMember;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationMemberRepository extends JpaRepository<ConversationMember, UUID> {

}
