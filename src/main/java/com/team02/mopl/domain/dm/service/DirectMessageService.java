package com.team02.mopl.domain.dm.service;

import static com.team02.mopl.global.exception.ErrorCode.SELF_CONVERSATION;
import static com.team02.mopl.global.exception.ErrorCode.USER_NOT_FOUND;

import com.team02.mopl.domain.dm.dto.ConversationCreateRequest;
import com.team02.mopl.domain.dm.dto.ConversationDto;
import com.team02.mopl.domain.dm.dto.ConversationDto.UserSummary;
import com.team02.mopl.domain.dm.entity.Conversation;
import com.team02.mopl.domain.dm.entity.ConversationMember;
import com.team02.mopl.domain.dm.repository.ConversationMemberRepository;
import com.team02.mopl.domain.dm.repository.ConversationRepository;
import com.team02.mopl.domain.dm.repository.DirectMessageRepository;
import com.team02.mopl.domain.user.entity.User;
import com.team02.mopl.domain.user.repository.UserRepository;
import com.team02.mopl.global.exception.BusinessException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DirectMessageService {

  private final DirectMessageRepository directMessageRepository;
  private final ConversationRepository conversationRepository;
  private final ConversationMemberRepository conversationMemberRepository;
  private final UserRepository userRepository;

  @Transactional
  public ConversationDto createConversation(ConversationCreateRequest request, UUID requesterId) {
    if (requesterId.equals(request.withUserId())) {
      throw new BusinessException(SELF_CONVERSATION);
    }
    User requestUser =
        userRepository
            .findById(requesterId)
            .orElseThrow(() -> new BusinessException(USER_NOT_FOUND));

    User withUser =
        userRepository
            .findById(request.withUserId())
            .orElseThrow(() -> new BusinessException(USER_NOT_FOUND));

    Conversation newConversation = conversationRepository.save(new Conversation());

    ConversationMember requestUserMember =
        ConversationMember.builder().conversation(newConversation).user(requestUser).build();

    ConversationMember withUserMember =
        ConversationMember.builder().conversation(newConversation).user(withUser).build();

    conversationMemberRepository.saveAll(java.util.List.of(requestUserMember, withUserMember));

    return new ConversationDto(
        newConversation.getId(),
        new UserSummary(withUser.getId(), withUser.getName(), withUser.getProfileImageUrl()),
        null,
        false);
  }
}
