package com.team02.mopl.domain.dm.service;

import static com.team02.mopl.global.exception.ErrorCode.CONVERSATION_ALREADY_EXISTS;
import static com.team02.mopl.global.exception.ErrorCode.CONVERSATION_NOT_FOUND;
import static com.team02.mopl.global.exception.ErrorCode.FORBIDDEN;
import static com.team02.mopl.global.exception.ErrorCode.SELF_CONVERSATION;
import static com.team02.mopl.global.exception.ErrorCode.USER_NOT_FOUND;

import com.team02.mopl.domain.dm.dto.ConversationCreateRequest;
import com.team02.mopl.domain.dm.dto.ConversationDto;
import com.team02.mopl.domain.dm.dto.DirectMessageDto;
import com.team02.mopl.domain.dm.entity.Conversation;
import com.team02.mopl.domain.dm.entity.ConversationMember;
import com.team02.mopl.domain.dm.entity.DirectMessage;
import com.team02.mopl.domain.dm.repository.ConversationMemberRepository;
import com.team02.mopl.domain.dm.repository.ConversationRepository;
import com.team02.mopl.domain.dm.repository.DirectMessageRepository;
import com.team02.mopl.domain.user.dto.UserSummary;
import com.team02.mopl.domain.user.entity.User;
import com.team02.mopl.domain.user.repository.UserRepository;
import com.team02.mopl.global.exception.BusinessException;
import java.time.Instant;
import java.util.Optional;
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

    if (conversationMemberRepository.existsConversationBetween(requesterId, request.withUserId())) {
      throw new BusinessException(CONVERSATION_ALREADY_EXISTS);
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

    Instant now = Instant.now();

    ConversationMember requestUserMember =
        ConversationMember.builder()
            .conversation(newConversation)
            .user(requestUser)
            .lastReadAt(now)
            .build();

    ConversationMember withUserMember =
        ConversationMember.builder()
            .conversation(newConversation)
            .user(withUser)
            .lastReadAt(now)
            .build();

    conversationMemberRepository.saveAll(java.util.List.of(requestUserMember, withUserMember));

    return new ConversationDto(
        newConversation.getId(),
        new UserSummary(withUser.getId(), withUser.getName(), withUser.getProfileImageUrl()),
        null,
        false);
  }

  @Transactional(readOnly = true)
  public ConversationDto findConversationWith(UUID requesterId, UUID withUserId) {
    if (requesterId.equals(withUserId)) {
      throw new BusinessException(SELF_CONVERSATION);
    }

    ConversationMember withUserMember =
        conversationMemberRepository
            .findWithUserMemberByUserIds(requesterId, withUserId)
            .orElseThrow(() -> new BusinessException(CONVERSATION_NOT_FOUND));

    UUID conversationId = withUserMember.getConversation().getId();
    ConversationMember requesterMember =
        conversationMemberRepository
            .findByConversationIdAndUserId(conversationId, requesterId)
            .orElseThrow(() -> new BusinessException(FORBIDDEN));

    User withUser = withUserMember.getUser();
    Optional<DirectMessage> lastDm =
        directMessageRepository.findFirstByConversationIdOrderByCreatedAtDescIdDesc(conversationId);

    return new ConversationDto(
        conversationId,
        new UserSummary(withUser.getId(), withUser.getName(), withUser.getProfileImageUrl()),
        lastDm.map(this::toDirectMessageDto).orElse(null),
        lastDm
            .filter(dm -> !dm.getSender().getUser().getId().equals(requesterId))
            .map(dm -> dm.getCreatedAt().isAfter(requesterMember.getLastReadAt()))
            .orElse(false));
  }

  @Transactional(readOnly = true)
  public ConversationDto findConversation(UUID conversationId, UUID requesterId) {
    if (!conversationRepository.existsById(conversationId)) {
      throw new BusinessException(CONVERSATION_NOT_FOUND);
    }

    ConversationMember withUserMember =
        conversationMemberRepository
            .findWithUserMember(conversationId, requesterId)
            .orElseThrow(() -> new BusinessException(FORBIDDEN));

    ConversationMember requesterMember =
        conversationMemberRepository
            .findByConversationIdAndUserId(conversationId, requesterId)
            .orElseThrow(() -> new BusinessException(FORBIDDEN));

    User withUser = withUserMember.getUser();
    Optional<DirectMessage> lastDm =
        directMessageRepository.findFirstByConversationIdOrderByCreatedAtDescIdDesc(conversationId);

    return new ConversationDto(
        conversationId,
        new UserSummary(withUser.getId(), withUser.getName(), withUser.getProfileImageUrl()),
        lastDm.map(this::toDirectMessageDto).orElse(null),
        lastDm
            .filter(dm -> !dm.getSender().getUser().getId().equals(requesterId))
            .map(dm -> dm.getCreatedAt().isAfter(requesterMember.getLastReadAt()))
            .orElse(false));
  }

  private DirectMessageDto toDirectMessageDto(DirectMessage dm) {
    User senderUser = dm.getSender().getUser();
    User receiverUser = dm.getReceiver().getUser();
    return new DirectMessageDto(
        dm.getId(),
        dm.getConversation().getId(),
        dm.getCreatedAt(),
        new UserSummary(senderUser.getId(), senderUser.getName(), senderUser.getProfileImageUrl()),
        new UserSummary(
            receiverUser.getId(), receiverUser.getName(), receiverUser.getProfileImageUrl()),
        dm.getContent());
  }
}
