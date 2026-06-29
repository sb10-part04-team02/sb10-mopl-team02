package com.team02.mopl.domain.dm.service;

import static com.team02.mopl.global.exception.ErrorCode.USER_NOT_FOUND;

import com.team02.mopl.domain.dm.dto.ConversationCreateRequest;
import com.team02.mopl.domain.dm.dto.ConversationDto;
import com.team02.mopl.domain.dm.dto.DirectMessageDto;
import com.team02.mopl.domain.dm.dto.DirectMessageSendRequest;
import com.team02.mopl.domain.dm.dto.DmSentEvent;
import com.team02.mopl.domain.dm.entity.Conversation;
import com.team02.mopl.domain.dm.entity.ConversationMember;
import com.team02.mopl.domain.dm.entity.DirectMessage;
import com.team02.mopl.domain.dm.exception.ConversationAlreadyExistsException;
import com.team02.mopl.domain.dm.exception.ConversationForbiddenException;
import com.team02.mopl.domain.dm.exception.ConversationNotFoundException;
import com.team02.mopl.domain.dm.exception.SelfConversationException;
import com.team02.mopl.domain.dm.repository.ConversationMemberRepository;
import com.team02.mopl.domain.dm.repository.ConversationRepository;
import com.team02.mopl.domain.dm.repository.DirectMessageRepository;
import com.team02.mopl.domain.notification.dto.NotificationCreateCommand;
import com.team02.mopl.domain.notification.entity.enums.NotificationLevel;
import com.team02.mopl.domain.notification.entity.enums.NotificationType;
import com.team02.mopl.domain.notification.service.NotificationService;
import com.team02.mopl.domain.user.dto.UserSummary;
import com.team02.mopl.domain.user.entity.User;
import com.team02.mopl.domain.user.repository.UserRepository;
import com.team02.mopl.global.exception.BusinessException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DirectMessageService {

  private final DirectMessageRepository directMessageRepository;
  private final ConversationRepository conversationRepository;
  private final ConversationMemberRepository conversationMemberRepository;
  private final UserRepository userRepository;
  private final NotificationService notificationService;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional
  public ConversationDto createConversation(ConversationCreateRequest request, UUID requesterId) {
    if (requesterId.equals(request.withUserId())) {
      throw new SelfConversationException();
    }

    if (conversationMemberRepository.existsConversationBetween(requesterId, request.withUserId())) {
      throw new ConversationAlreadyExistsException();
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
      throw new SelfConversationException();
    }

    ConversationMember withUserMember =
        conversationMemberRepository
            .findWithUserMemberByUserIds(requesterId, withUserId)
            .orElseThrow(ConversationNotFoundException::new);

    UUID conversationId = withUserMember.getConversation().getId();
    ConversationMember requesterMember =
        conversationMemberRepository
            .findByConversationIdAndUserId(conversationId, requesterId)
            .orElseThrow(ConversationForbiddenException::new);

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
      throw new ConversationNotFoundException();
    }

    ConversationMember withUserMember =
        conversationMemberRepository
            .findWithUserMember(conversationId, requesterId)
            .orElseThrow(ConversationForbiddenException::new);

    ConversationMember requesterMember =
        conversationMemberRepository
            .findByConversationIdAndUserId(conversationId, requesterId)
            .orElseThrow(ConversationForbiddenException::new);

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

  @Transactional
  public DirectMessageDto sendDirectMessage(
      UUID conversationId, UUID senderId, DirectMessageSendRequest request) {
    ConversationMember senderMember =
        conversationMemberRepository
            .findByConversationIdAndUserId(conversationId, senderId)
            .orElseThrow(ConversationForbiddenException::new);

    ConversationMember receiverMember =
        conversationMemberRepository
            .findWithUserMember(conversationId, senderId)
            .orElseThrow(ConversationForbiddenException::new);

    DirectMessage saved =
        directMessageRepository.save(
            DirectMessage.builder()
                .conversation(senderMember.getConversation())
                .sender(senderMember)
                .receiver(receiverMember)
                .content(request.content())
                .build());

    DirectMessageDto dto = toDirectMessageDto(saved);
    UUID receiverUserId = receiverMember.getUser().getId();

    // SSE 전송은 커밋 성공 후에만 발행 (AFTER_COMMIT)
    eventPublisher.publishEvent(new DmSentEvent(receiverUserId, saved.getId().toString(), dto));

    notificationService.createNotification(
        new NotificationCreateCommand(
            receiverUserId,
            "새 메시지",
            dto.sender().name() + "님이 메시지를 보냈습니다.",
            NotificationLevel.INFO,
            NotificationType.DIRECT_MESSAGE_RECEIVED));

    return dto;
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
