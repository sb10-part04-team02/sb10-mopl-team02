package com.team02.mopl.domain.dm.service;

import com.team02.mopl.domain.dm.repository.ConversationMemberRepository;
import com.team02.mopl.domain.dm.repository.ConversationRepository;
import com.team02.mopl.domain.dm.repository.DirectMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DirectMessageService {

  private final DirectMessageRepository directMessageRepository;
  private final ConversationRepository conversationRepository;
  private final ConversationMemberRepository conversationMemberRepository;

}
