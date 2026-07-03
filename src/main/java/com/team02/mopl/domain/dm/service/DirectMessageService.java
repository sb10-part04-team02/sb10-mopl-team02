package com.team02.mopl.domain.dm.service;

import static com.team02.mopl.global.exception.ErrorCode.USER_NOT_FOUND;

import com.team02.mopl.domain.dm.dto.ConversationCreateRequest;
import com.team02.mopl.domain.dm.dto.ConversationDto;
import com.team02.mopl.domain.dm.dto.ConversationSearchRequest;
import com.team02.mopl.domain.dm.dto.DirectMessageDto;
import com.team02.mopl.domain.dm.dto.DirectMessageSearchRequest;
import com.team02.mopl.domain.dm.dto.DirectMessageSendRequest;
import com.team02.mopl.domain.dm.dto.DmSentEvent;
import com.team02.mopl.domain.dm.entity.Conversation;
import com.team02.mopl.domain.dm.entity.ConversationMember;
import com.team02.mopl.domain.dm.entity.DirectMessage;
import com.team02.mopl.domain.dm.enums.ConversationSortBy;
import com.team02.mopl.domain.dm.enums.DirectMessageSortBy;
import com.team02.mopl.domain.dm.exception.ConversationAlreadyExistsException;
import com.team02.mopl.domain.dm.exception.ConversationForbiddenException;
import com.team02.mopl.domain.dm.exception.ConversationNotFoundException;
import com.team02.mopl.domain.dm.exception.SelfConversationException;
import com.team02.mopl.domain.dm.repository.ConversationMemberRepository;
import com.team02.mopl.domain.dm.repository.ConversationRepository;
import com.team02.mopl.domain.dm.repository.DirectMessageRepository;
import com.team02.mopl.domain.dm.util.ConversationCursorConverter;
import com.team02.mopl.domain.dm.util.DirectMessageCursorConverter;
import com.team02.mopl.domain.user.dto.UserSummary;
import com.team02.mopl.domain.user.entity.User;
import com.team02.mopl.domain.user.repository.UserRepository;
import com.team02.mopl.global.dto.CursorPageRequest;
import com.team02.mopl.global.dto.CursorResponse;
import com.team02.mopl.global.enums.SortDirection;
import com.team02.mopl.global.exception.BusinessException;
import com.team02.mopl.global.exception.ErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
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

  @Transactional(readOnly = true)
  public CursorResponse<ConversationDto> getConversations(
      UUID requesterId, ConversationSearchRequest request) {
    int limit = CursorPageRequest.normalizeLimit(request.limit());
    SortDirection direction = CursorPageRequest.normalizeSortDirection(request.sortDirection());

    // cursor와 idAfter는 함께 있거나 함께 없어야 한다 (첫 페이지: 둘 다 null)
    boolean hasCursor = request.cursor() != null && !request.cursor().isBlank();
    boolean hasIdAfter = request.idAfter() != null;
    if (hasCursor != hasIdAfter) {
      throw new BusinessException(ErrorCode.INVALID_REQUEST);
    }

    Instant cursor =
        ConversationCursorConverter.toSortKey(ConversationSortBy.CREATED_AT, request.cursor());

    List<Conversation> conversations =
        conversationRepository.findConversationsByCursor(
            requesterId, direction, cursor, request.idAfter(), limit + 1);

    boolean hasNext = conversations.size() > limit;
    List<Conversation> page = hasNext ? conversations.subList(0, limit) : conversations;

    List<ConversationDto> data = buildConversationDtos(page, requesterId);
    long totalCount = conversationRepository.countByMemberUserId(requesterId);

    String nextCursor = null;
    UUID nextIdAfter = null;
    if (hasNext) {
      Conversation last = page.get(page.size() - 1);
      nextCursor = last.getCreatedAt().toString();
      nextIdAfter = last.getId();
    }

    return new CursorResponse<>(
        data,
        nextCursor,
        nextIdAfter,
        hasNext,
        totalCount,
        ConversationSortBy.CREATED_AT.name(),
        direction.name());
  }

  @Transactional(readOnly = true)
  public CursorResponse<DirectMessageDto> getDirectMessages(
      UUID conversationId, UUID requesterId, DirectMessageSearchRequest request) {
    if (!conversationMemberRepository.existsByConversationIdAndUserId(
        conversationId, requesterId)) {
      throw new ConversationForbiddenException();
    }

    int limit = CursorPageRequest.normalizeLimit(request.limit());
    SortDirection direction = CursorPageRequest.normalizeSortDirection(request.sortDirection());

    // cursor와 idAfter는 함께 있거나 함께 없어야 한다 (첫 페이지: 둘 다 null)
    boolean hasCursor = request.cursor() != null && !request.cursor().isBlank();
    boolean hasIdAfter = request.idAfter() != null;
    if (hasCursor != hasIdAfter) {
      throw new BusinessException(ErrorCode.INVALID_REQUEST);
    }

    Instant cursor =
        DirectMessageCursorConverter.toSortKey(DirectMessageSortBy.CREATED_AT, request.cursor());

    List<DirectMessage> messages =
        directMessageRepository.findDirectMessagesByCursor(
            conversationId, direction, cursor, request.idAfter(), limit + 1);

    boolean hasNext = messages.size() > limit;
    List<DirectMessage> page = hasNext ? messages.subList(0, limit) : messages;

    List<DirectMessageDto> data = page.stream().map(this::toDirectMessageDto).toList();
    long totalCount = directMessageRepository.countByConversationId(conversationId);

    String nextCursor = null;
    UUID nextIdAfter = null;
    if (hasNext) {
      DirectMessage last = page.get(page.size() - 1);
      nextCursor = last.getCreatedAt().toString();
      nextIdAfter = last.getId();
    }

    return new CursorResponse<>(
        data,
        nextCursor,
        nextIdAfter,
        hasNext,
        totalCount,
        DirectMessageSortBy.CREATED_AT.name(),
        direction.name());
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

    // SSE 전송 및 알림 생성은 커밋 성공 후에만 실행 (AFTER_COMMIT)
    eventPublisher.publishEvent(new DmSentEvent(receiverUserId, saved.getId().toString(), dto));

    return dto;
  }

  private List<ConversationDto> buildConversationDtos(List<Conversation> page, UUID requesterId) {
    if (page.isEmpty()) {
      return List.of();
    }
    List<UUID> conversationIds = page.stream().map(Conversation::getId).toList();

    Map<UUID, ConversationMember> withUserMemberByConvId =
        conversationMemberRepository
            .findWithUserMembersForConversations(conversationIds, requesterId)
            .stream()
            .collect(Collectors.toMap(cm -> cm.getConversation().getId(), cm -> cm));

    Map<UUID, ConversationMember> requesterMemberByConvId =
        conversationMemberRepository
            .findByConversationIdsAndUserId(conversationIds, requesterId)
            .stream()
            .collect(Collectors.toMap(cm -> cm.getConversation().getId(), cm -> cm));

    List<UUID> lastMessageIds =
        directMessageRepository.findLatestMessageIdsByConversationIds(conversationIds);
    Map<UUID, DirectMessage> lastDmByConvId =
        directMessageRepository.findByIdIn(lastMessageIds).stream()
            .collect(Collectors.toMap(dm -> dm.getConversation().getId(), dm -> dm));

    return page.stream()
        .filter(
            conv ->
                withUserMemberByConvId.containsKey(conv.getId())
                    && requesterMemberByConvId.containsKey(conv.getId()))
        .map(
            conv -> {
              UUID convId = conv.getId();
              ConversationMember withUserMember = withUserMemberByConvId.get(convId);
              ConversationMember requesterMember = requesterMemberByConvId.get(convId);
              User withUser = withUserMember.getUser();
              DirectMessage lastDm = lastDmByConvId.get(convId);
              boolean hasUnread =
                  lastDm != null
                      && !lastDm.getSender().getUser().getId().equals(requesterId)
                      && lastDm.getCreatedAt().isAfter(requesterMember.getLastReadAt());
              return new ConversationDto(
                  convId,
                  new UserSummary(
                      withUser.getId(), withUser.getName(), withUser.getProfileImageUrl()),
                  lastDm != null ? toDirectMessageDto(lastDm) : null,
                  hasUnread);
            })
        .toList();
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
