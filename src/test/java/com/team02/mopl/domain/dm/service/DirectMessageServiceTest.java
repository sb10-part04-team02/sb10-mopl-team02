package com.team02.mopl.domain.dm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
import com.team02.mopl.domain.dm.repository.ConversationMemberRepository;
import com.team02.mopl.domain.dm.repository.ConversationRepository;
import com.team02.mopl.domain.dm.repository.DirectMessageRepository;
import com.team02.mopl.domain.user.entity.User;
import com.team02.mopl.domain.user.repository.UserRepository;
import com.team02.mopl.global.dto.CursorResponse;
import com.team02.mopl.global.enums.SortDirection;
import com.team02.mopl.global.exception.BusinessException;
import com.team02.mopl.global.exception.ErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DirectMessageServiceTest {

  @Mock private DirectMessageRepository directMessageRepository;
  @Mock private ConversationRepository conversationRepository;
  @Mock private ConversationMemberRepository conversationMemberRepository;
  @Mock private UserRepository userRepository;
  @Mock private ApplicationEventPublisher eventPublisher;

  @InjectMocks private DirectMessageService directMessageService;

  @Test
  @DisplayName("정상적으로 대화방을 생성하고 ConversationDto를 반환한다")
  void createConversation_success() {
    UUID requesterId = UUID.randomUUID();
    UUID withUserId = UUID.randomUUID();
    UUID conversationId = UUID.randomUUID();

    User requester = mockUser(requesterId, "요청자", "requester@example.com", null);
    User withUser =
        mockUser(withUserId, "상대방", "with@example.com", "https://img.example.com/profile.png");
    Conversation savedConversation = mock(Conversation.class);

    given(savedConversation.getId()).willReturn(conversationId);
    given(userRepository.findById(requesterId)).willReturn(Optional.of(requester));
    given(userRepository.findById(withUserId)).willReturn(Optional.of(withUser));
    given(conversationRepository.save(any(Conversation.class))).willReturn(savedConversation);
    given(conversationMemberRepository.saveAll(any(List.class))).willReturn(List.of());

    ConversationDto result =
        directMessageService.createConversation(
            new ConversationCreateRequest(withUserId), requesterId);

    assertThat(result.id()).isEqualTo(conversationId);
    assertThat(result.with().userId()).isEqualTo(withUserId);
    assertThat(result.with().name()).isEqualTo("상대방");
    assertThat(result.with().profileImageUrl()).isEqualTo("https://img.example.com/profile.png");
    assertThat(result.lastMessage()).isNull();
    assertThat(result.hasUnread()).isFalse();

    verify(conversationRepository).save(any(Conversation.class));
    verify(conversationMemberRepository).saveAll(any(List.class));
  }

  @Test
  @DisplayName("자기 자신과 대화방을 생성하려 하면 SELF_CONVERSATION 예외가 발생한다")
  void createConversation_selfConversation_throwsException() {
    UUID requesterId = UUID.randomUUID();

    assertThatThrownBy(
            () ->
                directMessageService.createConversation(
                    new ConversationCreateRequest(requesterId), requesterId))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SELF_CONVERSATION));

    verify(userRepository, never()).findById(any());
    verify(conversationRepository, never()).save(any());
  }

  @Test
  @DisplayName("두 사용자 간 대화방이 이미 존재하면 CONVERSATION_ALREADY_EXISTS 예외가 발생한다")
  void createConversation_alreadyExists_throwsException() {
    UUID requesterId = UUID.randomUUID();
    UUID withUserId = UUID.randomUUID();

    given(conversationMemberRepository.existsConversationBetween(requesterId, withUserId))
        .willReturn(true);

    assertThatThrownBy(
            () ->
                directMessageService.createConversation(
                    new ConversationCreateRequest(withUserId), requesterId))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CONVERSATION_ALREADY_EXISTS));

    verify(userRepository, never()).findById(any());
    verify(conversationRepository, never()).save(any());
  }

  @Test
  @DisplayName("요청자가 존재하지 않으면 USER_NOT_FOUND 예외가 발생한다")
  void createConversation_requesterNotFound_throwsException() {
    UUID requesterId = UUID.randomUUID();
    UUID withUserId = UUID.randomUUID();

    given(userRepository.findById(requesterId)).willReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                directMessageService.createConversation(
                    new ConversationCreateRequest(withUserId), requesterId))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND));

    verify(conversationRepository, never()).save(any());
  }

  @Test
  @DisplayName("상대방이 존재하지 않으면 USER_NOT_FOUND 예외가 발생한다")
  void createConversation_withUserNotFound_throwsException() {
    UUID requesterId = UUID.randomUUID();
    UUID withUserId = UUID.randomUUID();

    User requester = mockUser(requesterId, "요청자", "requester@example.com", null);

    given(userRepository.findById(requesterId)).willReturn(Optional.of(requester));
    given(userRepository.findById(withUserId)).willReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                directMessageService.createConversation(
                    new ConversationCreateRequest(withUserId), requesterId))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND));

    verify(conversationRepository, never()).save(any());
  }

  @Test
  @DisplayName("대화방 생성 시 요청자와 상대방 두 명이 ConversationMember로 저장된다")
  void createConversation_savesBothMembersToRepository() {
    UUID requesterId = UUID.randomUUID();
    UUID withUserId = UUID.randomUUID();

    User requester = mockUser(requesterId, "요청자", "requester@example.com", null);
    User withUser = mockUser(withUserId, "상대방", "with@example.com", null);
    Conversation savedConversation = mock(Conversation.class);

    given(savedConversation.getId()).willReturn(UUID.randomUUID());
    given(userRepository.findById(requesterId)).willReturn(Optional.of(requester));
    given(userRepository.findById(withUserId)).willReturn(Optional.of(withUser));
    given(conversationRepository.save(any(Conversation.class))).willReturn(savedConversation);
    given(conversationMemberRepository.saveAll(any(List.class)))
        .willAnswer(
            invocation -> {
              List<ConversationMember> members = invocation.getArgument(0);
              assertThat(members).hasSize(2);
              return members;
            });

    directMessageService.createConversation(new ConversationCreateRequest(withUserId), requesterId);

    verify(conversationMemberRepository).saveAll(any(List.class));
  }

  // ──────────────────────────────────────────────
  // findConversationWith
  // ──────────────────────────────────────────────

  @Test
  @DisplayName("자기 자신과의 대화방 조회 시 SELF_CONVERSATION 예외가 발생한다")
  void findConversationWith_selfConversation_throwsException() {
    UUID userId = UUID.randomUUID();

    assertThatThrownBy(() -> directMessageService.findConversationWith(userId, userId))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SELF_CONVERSATION));
  }

  @Test
  @DisplayName("대화방이 존재하지 않으면 CONVERSATION_NOT_FOUND 예외가 발생한다")
  void findConversationWith_notFound_throwsException() {
    UUID requesterId = UUID.randomUUID();
    UUID withUserId = UUID.randomUUID();

    given(conversationMemberRepository.findWithUserMemberByUserIds(requesterId, withUserId))
        .willReturn(Optional.empty());

    assertThatThrownBy(() -> directMessageService.findConversationWith(requesterId, withUserId))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CONVERSATION_NOT_FOUND));
  }

  @Test
  @DisplayName("마지막 메시지가 없으면 lastMessage=null, hasUnread=false로 반환한다")
  void findConversationWith_noLastMessage_returnsNullLastMessageAndFalseHasUnread() {
    UUID requesterId = UUID.randomUUID();
    UUID withUserId = UUID.randomUUID();
    UUID conversationId = UUID.randomUUID();

    ConversationMember withUserMember = mockWithUserMember(conversationId, withUserId, "상대방", null);
    ConversationMember requesterMember = mockConversationMember(requesterId, Instant.now());

    given(conversationMemberRepository.findWithUserMemberByUserIds(requesterId, withUserId))
        .willReturn(Optional.of(withUserMember));
    given(conversationMemberRepository.findByConversationIdAndUserId(conversationId, requesterId))
        .willReturn(Optional.of(requesterMember));
    given(
            directMessageRepository.findFirstByConversationIdOrderByCreatedAtDescIdDesc(
                conversationId))
        .willReturn(Optional.empty());

    ConversationDto result = directMessageService.findConversationWith(requesterId, withUserId);

    assertThat(result.lastMessage()).isNull();
    assertThat(result.hasUnread()).isFalse();
  }

  @Test
  @DisplayName("상대방이 보낸 마지막 메시지가 lastReadAt 이후면 hasUnread=true로 반환한다")
  void findConversationWith_lastMessageAfterLastReadAt_hasUnreadTrue() {
    UUID requesterId = UUID.randomUUID();
    UUID withUserId = UUID.randomUUID();
    UUID conversationId = UUID.randomUUID();
    Instant lastReadAt = Instant.parse("2024-01-01T00:00:00Z");
    Instant messageAt = Instant.parse("2024-01-02T00:00:00Z");

    ConversationMember withUserMember = mockWithUserMember(conversationId, withUserId, "상대방", null);
    ConversationMember requesterMember = mockConversationMember(requesterId, lastReadAt);
    DirectMessage lastDm = mockDirectMessage(conversationId, withUserId, requesterId, messageAt);

    given(conversationMemberRepository.findWithUserMemberByUserIds(requesterId, withUserId))
        .willReturn(Optional.of(withUserMember));
    given(conversationMemberRepository.findByConversationIdAndUserId(conversationId, requesterId))
        .willReturn(Optional.of(requesterMember));
    given(
            directMessageRepository.findFirstByConversationIdOrderByCreatedAtDescIdDesc(
                conversationId))
        .willReturn(Optional.of(lastDm));

    ConversationDto result = directMessageService.findConversationWith(requesterId, withUserId);

    assertThat(result.hasUnread()).isTrue();
    assertThat(result.lastMessage()).isNotNull();
    assertThat(result.lastMessage().content()).isEqualTo("안녕하세요");
  }

  @Test
  @DisplayName("상대방이 보낸 마지막 메시지가 lastReadAt 이전이면 hasUnread=false로 반환한다")
  void findConversationWith_lastMessageBeforeLastReadAt_hasUnreadFalse() {
    UUID requesterId = UUID.randomUUID();
    UUID withUserId = UUID.randomUUID();
    UUID conversationId = UUID.randomUUID();
    Instant messageAt = Instant.parse("2024-01-01T00:00:00Z");
    Instant lastReadAt = Instant.parse("2024-01-02T00:00:00Z");

    ConversationMember withUserMember = mockWithUserMember(conversationId, withUserId, "상대방", null);
    ConversationMember requesterMember = mockConversationMember(requesterId, lastReadAt);
    DirectMessage lastDm = mockDirectMessage(conversationId, withUserId, requesterId, messageAt);

    given(conversationMemberRepository.findWithUserMemberByUserIds(requesterId, withUserId))
        .willReturn(Optional.of(withUserMember));
    given(conversationMemberRepository.findByConversationIdAndUserId(conversationId, requesterId))
        .willReturn(Optional.of(requesterMember));
    given(
            directMessageRepository.findFirstByConversationIdOrderByCreatedAtDescIdDesc(
                conversationId))
        .willReturn(Optional.of(lastDm));

    ConversationDto result = directMessageService.findConversationWith(requesterId, withUserId);

    assertThat(result.hasUnread()).isFalse();
  }

  @Test
  @DisplayName("마지막 메시지가 요청자 본인이 보낸 것이면 lastReadAt 이후여도 hasUnread=false로 반환한다")
  void findConversationWith_selfSentLastMessage_hasUnreadFalseEvenIfAfterLastReadAt() {
    UUID requesterId = UUID.randomUUID();
    UUID withUserId = UUID.randomUUID();
    UUID conversationId = UUID.randomUUID();
    Instant lastReadAt = Instant.parse("2024-01-01T00:00:00Z");
    Instant messageAt = Instant.parse("2024-01-02T00:00:00Z");

    ConversationMember withUserMember = mockWithUserMember(conversationId, withUserId, "상대방", null);
    ConversationMember requesterMember = mockConversationMember(requesterId, lastReadAt);
    DirectMessage lastDm = mockDirectMessage(conversationId, requesterId, withUserId, messageAt);

    given(conversationMemberRepository.findWithUserMemberByUserIds(requesterId, withUserId))
        .willReturn(Optional.of(withUserMember));
    given(conversationMemberRepository.findByConversationIdAndUserId(conversationId, requesterId))
        .willReturn(Optional.of(requesterMember));
    given(
            directMessageRepository.findFirstByConversationIdOrderByCreatedAtDescIdDesc(
                conversationId))
        .willReturn(Optional.of(lastDm));

    ConversationDto result = directMessageService.findConversationWith(requesterId, withUserId);

    assertThat(result.hasUnread()).isFalse();
  }

  // ──────────────────────────────────────────────
  // findConversation
  // ──────────────────────────────────────────────

  @Test
  @DisplayName("존재하지 않는 대화방 ID 조회 시 CONVERSATION_NOT_FOUND 예외가 발생한다")
  void findConversation_notFound_throwsException() {
    UUID conversationId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();

    given(conversationRepository.existsById(conversationId)).willReturn(false);

    assertThatThrownBy(() -> directMessageService.findConversation(conversationId, requesterId))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CONVERSATION_NOT_FOUND));
  }

  @Test
  @DisplayName("요청자가 대화방 멤버가 아니면 FORBIDDEN 예외가 발생한다")
  void findConversation_notMember_throwsForbidden() {
    UUID conversationId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();

    given(conversationRepository.existsById(conversationId)).willReturn(true);
    given(conversationMemberRepository.findWithUserMember(conversationId, requesterId))
        .willReturn(Optional.empty());

    assertThatThrownBy(() -> directMessageService.findConversation(conversationId, requesterId))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
  }

  @Test
  @DisplayName("마지막 메시지가 있고 읽지 않은 경우 hasUnread=true로 반환한다")
  void findConversation_withUnreadLastMessage_returnsHasUnreadTrue() {
    UUID conversationId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();
    UUID withUserId = UUID.randomUUID();
    Instant lastReadAt = Instant.parse("2024-01-01T00:00:00Z");
    Instant messageAt = Instant.parse("2024-01-02T00:00:00Z");

    ConversationMember withUserMember = mockConversationMemberWithUser(withUserId, "상대방", null);
    ConversationMember requesterMember = mockConversationMember(requesterId, lastReadAt);
    DirectMessage lastDm = mockDirectMessage(conversationId, withUserId, requesterId, messageAt);

    given(conversationRepository.existsById(conversationId)).willReturn(true);
    given(conversationMemberRepository.findWithUserMember(conversationId, requesterId))
        .willReturn(Optional.of(withUserMember));
    given(conversationMemberRepository.findByConversationIdAndUserId(conversationId, requesterId))
        .willReturn(Optional.of(requesterMember));
    given(
            directMessageRepository.findFirstByConversationIdOrderByCreatedAtDescIdDesc(
                conversationId))
        .willReturn(Optional.of(lastDm));

    ConversationDto result = directMessageService.findConversation(conversationId, requesterId);

    assertThat(result.id()).isEqualTo(conversationId);
    assertThat(result.with().userId()).isEqualTo(withUserId);
    assertThat(result.hasUnread()).isTrue();
    assertThat(result.lastMessage()).isNotNull();
    assertThat(result.lastMessage().content()).isEqualTo("안녕하세요");
  }

  @Test
  @DisplayName("마지막 메시지가 요청자 본인이 보낸 것이면 lastReadAt 이후여도 hasUnread=false로 반환한다")
  void findConversation_selfSentLastMessage_hasUnreadFalseEvenIfAfterLastReadAt() {
    UUID conversationId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();
    UUID withUserId = UUID.randomUUID();
    Instant lastReadAt = Instant.parse("2024-01-01T00:00:00Z");
    Instant messageAt = Instant.parse("2024-01-02T00:00:00Z");

    ConversationMember withUserMember = mockConversationMemberWithUser(withUserId, "상대방", null);
    ConversationMember requesterMember = mockConversationMember(requesterId, lastReadAt);
    DirectMessage lastDm = mockDirectMessage(conversationId, requesterId, withUserId, messageAt);

    given(conversationRepository.existsById(conversationId)).willReturn(true);
    given(conversationMemberRepository.findWithUserMember(conversationId, requesterId))
        .willReturn(Optional.of(withUserMember));
    given(conversationMemberRepository.findByConversationIdAndUserId(conversationId, requesterId))
        .willReturn(Optional.of(requesterMember));
    given(
            directMessageRepository.findFirstByConversationIdOrderByCreatedAtDescIdDesc(
                conversationId))
        .willReturn(Optional.of(lastDm));

    ConversationDto result = directMessageService.findConversation(conversationId, requesterId);

    assertThat(result.hasUnread()).isFalse();
  }

  @Test
  @DisplayName("마지막 메시지가 없으면 lastMessage=null, hasUnread=false로 반환한다")
  void findConversation_noLastMessage_returnsNullLastMessageAndFalseHasUnread() {
    UUID conversationId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();
    UUID withUserId = UUID.randomUUID();

    ConversationMember withUserMember = mockConversationMemberWithUser(withUserId, "상대방", null);
    ConversationMember requesterMember = mockConversationMember(requesterId, Instant.now());

    given(conversationRepository.existsById(conversationId)).willReturn(true);
    given(conversationMemberRepository.findWithUserMember(conversationId, requesterId))
        .willReturn(Optional.of(withUserMember));
    given(conversationMemberRepository.findByConversationIdAndUserId(conversationId, requesterId))
        .willReturn(Optional.of(requesterMember));
    given(
            directMessageRepository.findFirstByConversationIdOrderByCreatedAtDescIdDesc(
                conversationId))
        .willReturn(Optional.empty());

    ConversationDto result = directMessageService.findConversation(conversationId, requesterId);

    assertThat(result.lastMessage()).isNull();
    assertThat(result.hasUnread()).isFalse();
  }

  // ──────────────────────────────────────────────
  // getConversations
  // ──────────────────────────────────────────────

  @Test
  @DisplayName("대화 목록을 조회하면 CursorResponse를 반환한다")
  void getConversations_success_returnsCursorResponse() {
    UUID requesterId = UUID.randomUUID();
    UUID withUserId = UUID.randomUUID();
    UUID conversationId = UUID.randomUUID();

    Conversation conv = mock(Conversation.class);
    given(conv.getId()).willReturn(conversationId);
    given(conv.getCreatedAt()).willReturn(Instant.now());

    ConversationMember withUserMember =
        mockConversationMemberWithConvId(conversationId, withUserId, "상대방", null);
    ConversationMember requesterMember =
        mockConversationMemberWithConvId(conversationId, requesterId, null, null);

    given(
            conversationRepository.findConversationsByCursor(
                requesterId, SortDirection.DESCENDING, null, null, 21))
        .willReturn(List.of(conv));
    given(conversationRepository.countByMemberUserId(requesterId)).willReturn(1L);
    given(
            conversationMemberRepository.findWithUserMembersForConversations(
                List.of(conversationId), requesterId))
        .willReturn(List.of(withUserMember));
    given(
            conversationMemberRepository.findByConversationIdsAndUserId(
                List.of(conversationId), requesterId))
        .willReturn(List.of(requesterMember));
    given(directMessageRepository.findLatestMessageIdsByConversationIds(List.of(conversationId)))
        .willReturn(List.of());
    given(directMessageRepository.findByIdIn(List.of())).willReturn(List.of());

    CursorResponse<ConversationDto> result =
        directMessageService.getConversations(
            requesterId, new ConversationSearchRequest(null, null, null, null, null));

    assertThat(result.data()).hasSize(1);
    assertThat(result.data().get(0).id()).isEqualTo(conversationId);
    assertThat(result.data().get(0).with().userId()).isEqualTo(withUserId);
    assertThat(result.hasNext()).isFalse();
    assertThat(result.totalCount()).isEqualTo(1L);
    assertThat(result.sortBy()).isEqualTo(ConversationSortBy.CREATED_AT.name());
    assertThat(result.sortDirection()).isEqualTo(SortDirection.DESCENDING.name());
  }

  @Test
  @DisplayName("대화 목록이 limit보다 하나 많으면 hasNext=true이고 nextCursor가 설정된다")
  void getConversations_hasNext_whenResultsExceedLimit() {
    UUID requesterId = UUID.randomUUID();
    UUID withUserId = UUID.randomUUID();

    UUID convId1 = UUID.randomUUID();
    UUID convId2 = UUID.randomUUID();
    Instant t1 = Instant.parse("2026-06-29T02:00:00Z");
    Instant t2 = Instant.parse("2026-06-29T01:00:00Z");

    Conversation conv1 = mock(Conversation.class);
    given(conv1.getId()).willReturn(convId1);
    given(conv1.getCreatedAt()).willReturn(t1);

    Conversation conv2 = mock(Conversation.class);
    given(conv2.getId()).willReturn(convId2);
    given(conv2.getCreatedAt()).willReturn(t2);

    // limit=1 → page=[conv1], batch queries only for convId1
    ConversationMember withMember =
        mockConversationMemberWithConvId(convId1, withUserId, "상대방", null);
    ConversationMember requesterMember =
        mockConversationMemberWithConvId(convId1, requesterId, null, null);

    given(
            conversationRepository.findConversationsByCursor(
                requesterId, SortDirection.DESCENDING, null, null, 2))
        .willReturn(List.of(conv1, conv2));
    given(conversationRepository.countByMemberUserId(requesterId)).willReturn(3L);
    given(
            conversationMemberRepository.findWithUserMembersForConversations(
                List.of(convId1), requesterId))
        .willReturn(List.of(withMember));
    given(
            conversationMemberRepository.findByConversationIdsAndUserId(
                List.of(convId1), requesterId))
        .willReturn(List.of(requesterMember));
    given(directMessageRepository.findLatestMessageIdsByConversationIds(List.of(convId1)))
        .willReturn(List.of());
    given(directMessageRepository.findByIdIn(List.of())).willReturn(List.of());

    CursorResponse<ConversationDto> result =
        directMessageService.getConversations(
            requesterId, new ConversationSearchRequest(null, null, 1, null, null));

    assertThat(result.data()).hasSize(1);
    assertThat(result.hasNext()).isTrue();
    assertThat(result.nextCursor()).isEqualTo(t1.toString());
    assertThat(result.nextIdAfter()).isEqualTo(convId1);
  }

  @Test
  @DisplayName("대화 목록의 마지막 메시지와 읽지 않음 여부가 올바르게 설정된다")
  void getConversations_withLastMessage_setsHasUnreadCorrectly() {
    UUID requesterId = UUID.randomUUID();
    UUID withUserId = UUID.randomUUID();
    UUID conversationId = UUID.randomUUID();
    Instant lastReadAt = Instant.parse("2026-06-29T01:00:00Z");
    Instant messageAt = Instant.parse("2026-06-29T02:00:00Z");

    Conversation conv = mock(Conversation.class);
    given(conv.getId()).willReturn(conversationId);
    given(conv.getCreatedAt()).willReturn(Instant.now());

    ConversationMember withUserMember =
        mockConversationMemberWithConvId(conversationId, withUserId, "상대방", null);
    ConversationMember requesterMember =
        mockConversationMemberWithConvId(conversationId, requesterId, null, null);
    given(requesterMember.getLastReadAt()).willReturn(lastReadAt);
    DirectMessage lastDm = mockDirectMessage(conversationId, withUserId, requesterId, messageAt);
    UUID lastDmId = lastDm.getId();

    given(
            conversationRepository.findConversationsByCursor(
                requesterId, SortDirection.DESCENDING, null, null, 21))
        .willReturn(List.of(conv));
    given(conversationRepository.countByMemberUserId(requesterId)).willReturn(1L);
    given(
            conversationMemberRepository.findWithUserMembersForConversations(
                List.of(conversationId), requesterId))
        .willReturn(List.of(withUserMember));
    given(
            conversationMemberRepository.findByConversationIdsAndUserId(
                List.of(conversationId), requesterId))
        .willReturn(List.of(requesterMember));
    given(directMessageRepository.findLatestMessageIdsByConversationIds(List.of(conversationId)))
        .willReturn(List.of(lastDmId));
    given(directMessageRepository.findByIdIn(List.of(lastDmId))).willReturn(List.of(lastDm));

    CursorResponse<ConversationDto> result =
        directMessageService.getConversations(
            requesterId, new ConversationSearchRequest(null, null, null, null, null));

    assertThat(result.data().get(0).hasUnread()).isTrue();
    assertThat(result.data().get(0).lastMessage()).isNotNull();
    assertThat(result.data().get(0).lastMessage().content()).isEqualTo("안녕하세요");
  }

  @Test
  @DisplayName("cursor/idAfter와 ASCENDING 정렬 요청을 리포지토리에 그대로 전달한다")
  void getConversations_withCursorAndAscending_forwardsPaginationArguments() {
    UUID requesterId = UUID.randomUUID();
    UUID idAfter = UUID.randomUUID();
    String cursor = "2026-06-30T10:15:30Z";

    given(
            conversationRepository.findConversationsByCursor(
                requesterId, SortDirection.ASCENDING, cursor, idAfter, 6))
        .willReturn(List.of());
    given(conversationRepository.countByMemberUserId(requesterId)).willReturn(0L);

    CursorResponse<ConversationDto> result =
        directMessageService.getConversations(
            requesterId,
            new ConversationSearchRequest(
                cursor, idAfter, 5, SortDirection.ASCENDING, ConversationSortBy.CREATED_AT));

    verify(conversationRepository)
        .findConversationsByCursor(requesterId, SortDirection.ASCENDING, cursor, idAfter, 6);
    assertThat(result.sortDirection()).isEqualTo(SortDirection.ASCENDING.name());
    assertThat(result.data()).isEmpty();
  }

  // ──────────────────────────────────────────────
  // getDirectMessages
  // ──────────────────────────────────────────────

  @Test
  @DisplayName("참여자가 DM 목록을 조회하면 CursorResponse를 반환한다")
  void getDirectMessages_success_returnsCursorResponse() {
    UUID conversationId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();
    UUID senderId = UUID.randomUUID();
    UUID receiverId = UUID.randomUUID();

    DirectMessage dm1 = mockDirectMessage(conversationId, senderId, receiverId, Instant.now());
    DirectMessage dm2 = mockDirectMessage(conversationId, senderId, receiverId, Instant.now());

    given(conversationMemberRepository.existsByConversationIdAndUserId(conversationId, requesterId))
        .willReturn(true);
    given(
            directMessageRepository.findDirectMessagesByCursor(
                conversationId, SortDirection.DESCENDING, null, null, 21))
        .willReturn(List.of(dm1, dm2));
    given(directMessageRepository.countByConversationId(conversationId)).willReturn(2L);

    DirectMessageSearchRequest request =
        new DirectMessageSearchRequest(null, null, null, null, null);
    CursorResponse<DirectMessageDto> result =
        directMessageService.getDirectMessages(conversationId, requesterId, request);

    assertThat(result.data()).hasSize(2);
    assertThat(result.hasNext()).isFalse();
    assertThat(result.totalCount()).isEqualTo(2L);
    assertThat(result.sortBy()).isEqualTo(DirectMessageSortBy.CREATED_AT.name());
    assertThat(result.sortDirection()).isEqualTo(SortDirection.DESCENDING.name());
  }

  @Test
  @DisplayName("조회 결과가 limit+1개이면 hasNext=true이고 nextCursor가 설정된다")
  void getDirectMessages_hasNext_whenResultsExceedLimit() {
    UUID conversationId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();
    UUID senderId = UUID.randomUUID();
    UUID receiverId = UUID.randomUUID();

    Instant now = Instant.now();
    DirectMessage dm1 = mockDirectMessage(conversationId, senderId, receiverId, now);
    DirectMessage dm2 = mockDirectMessage(conversationId, senderId, receiverId, now);
    UUID lastId = UUID.randomUUID();
    given(dm2.getId()).willReturn(lastId);
    given(dm2.getCreatedAt()).willReturn(now);

    given(conversationMemberRepository.existsByConversationIdAndUserId(conversationId, requesterId))
        .willReturn(true);
    // limit=1 → limit+1=2개 요청, 2개 반환 → hasNext=true
    given(
            directMessageRepository.findDirectMessagesByCursor(
                conversationId, SortDirection.DESCENDING, null, null, 2))
        .willReturn(List.of(dm1, dm2));
    given(directMessageRepository.countByConversationId(conversationId)).willReturn(5L);

    DirectMessageSearchRequest request = new DirectMessageSearchRequest(null, null, 1, null, null);
    CursorResponse<DirectMessageDto> result =
        directMessageService.getDirectMessages(conversationId, requesterId, request);

    assertThat(result.data()).hasSize(1);
    assertThat(result.hasNext()).isTrue();
    assertThat(result.nextIdAfter()).isEqualTo(dm1.getId());
    assertThat(result.nextCursor()).isEqualTo(dm1.getCreatedAt().toString());
  }

  @Test
  @DisplayName("대화방 참여자가 아니면 FORBIDDEN 예외가 발생한다")
  void getDirectMessages_notMember_throwsForbidden() {
    UUID conversationId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();

    given(conversationMemberRepository.existsByConversationIdAndUserId(conversationId, requesterId))
        .willReturn(false);

    assertThatThrownBy(
            () ->
                directMessageService.getDirectMessages(
                    conversationId,
                    requesterId,
                    new DirectMessageSearchRequest(null, null, null, null, null)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
  }

  @Test
  @DisplayName("cursor/idAfter와 ASCENDING 정렬 요청을 리포지토리에 그대로 전달한다")
  void getDirectMessages_withCursorAndAscending_forwardsPaginationArguments() {
    UUID conversationId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();
    UUID idAfter = UUID.randomUUID();
    String cursor = "2026-06-30T10:15:30Z";

    given(conversationMemberRepository.existsByConversationIdAndUserId(conversationId, requesterId))
        .willReturn(true);
    given(
            directMessageRepository.findDirectMessagesByCursor(
                conversationId, SortDirection.ASCENDING, cursor, idAfter, 6))
        .willReturn(List.of());
    given(directMessageRepository.countByConversationId(conversationId)).willReturn(0L);

    CursorResponse<DirectMessageDto> result =
        directMessageService.getDirectMessages(
            conversationId,
            requesterId,
            new DirectMessageSearchRequest(
                cursor, idAfter, 5, SortDirection.ASCENDING, DirectMessageSortBy.CREATED_AT));

    verify(directMessageRepository)
        .findDirectMessagesByCursor(conversationId, SortDirection.ASCENDING, cursor, idAfter, 6);
    assertThat(result.sortDirection()).isEqualTo(SortDirection.ASCENDING.name());
    assertThat(result.sortBy()).isEqualTo(DirectMessageSortBy.CREATED_AT.name());
  }

  // ──────────────────────────────────────────────
  // sendDirectMessage
  // ──────────────────────────────────────────────

  @Test
  @DisplayName("정상적으로 메시지를 전송하고 DirectMessageDto를 반환한다")
  void sendDirectMessage_success() {
    UUID conversationId = UUID.randomUUID();
    UUID senderId = UUID.randomUUID();
    UUID receiverId = UUID.randomUUID();
    UUID messageId = UUID.randomUUID();

    ConversationMember senderMember = mock(ConversationMember.class);
    ConversationMember receiverMember = mockConversationMemberWithUser(receiverId, "수신자", null);
    Conversation conversation = mock(Conversation.class);
    given(conversation.getId()).willReturn(conversationId);
    given(senderMember.getConversation()).willReturn(conversation);

    DirectMessage saved = mockDirectMessage(conversationId, senderId, receiverId, Instant.now());
    given(saved.getId()).willReturn(messageId);
    given(saved.getContent()).willReturn("안녕하세요");

    given(conversationMemberRepository.findByConversationIdAndUserId(conversationId, senderId))
        .willReturn(Optional.of(senderMember));
    given(conversationMemberRepository.findWithUserMember(conversationId, senderId))
        .willReturn(Optional.of(receiverMember));
    given(directMessageRepository.save(any(DirectMessage.class))).willReturn(saved);

    DirectMessageDto result =
        directMessageService.sendDirectMessage(
            conversationId, senderId, new DirectMessageSendRequest("안녕하세요"));

    ArgumentCaptor<DirectMessage> captor = ArgumentCaptor.forClass(DirectMessage.class);
    verify(directMessageRepository).save(captor.capture());
    DirectMessage persisted = captor.getValue();
    assertThat(persisted.getConversation()).isSameAs(conversation);
    assertThat(persisted.getSender()).isSameAs(senderMember);
    assertThat(persisted.getReceiver()).isSameAs(receiverMember);
    assertThat(persisted.getContent()).isEqualTo("안녕하세요");

    assertThat(result.conversationId()).isEqualTo(conversationId);
    assertThat(result.content()).isEqualTo("안녕하세요");
    assertThat(result.sender().userId()).isEqualTo(senderId);
    assertThat(result.receiver().userId()).isEqualTo(receiverId);

    ArgumentCaptor<DmSentEvent> eventCaptor = ArgumentCaptor.forClass(DmSentEvent.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue().receiverUserId()).isEqualTo(receiverId);
    assertThat(eventCaptor.getValue().eventId()).isEqualTo(messageId.toString());
    assertThat(eventCaptor.getValue().dto()).isEqualTo(result);
  }

  @Test
  @DisplayName("발신자가 대화방 멤버가 아니면 FORBIDDEN 예외가 발생한다")
  void sendDirectMessage_senderNotMember_throwsForbidden() {
    UUID conversationId = UUID.randomUUID();
    UUID senderId = UUID.randomUUID();

    given(conversationMemberRepository.findByConversationIdAndUserId(conversationId, senderId))
        .willReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                directMessageService.sendDirectMessage(
                    conversationId, senderId, new DirectMessageSendRequest("안녕하세요")))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

    verify(directMessageRepository, never()).save(any());
    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  @DisplayName("수신자를 찾지 못하면 FORBIDDEN 예외가 발생한다")
  void sendDirectMessage_receiverNotFound_throwsForbidden() {
    UUID conversationId = UUID.randomUUID();
    UUID senderId = UUID.randomUUID();

    ConversationMember senderMember = mock(ConversationMember.class);

    given(conversationMemberRepository.findByConversationIdAndUserId(conversationId, senderId))
        .willReturn(Optional.of(senderMember));
    given(conversationMemberRepository.findWithUserMember(conversationId, senderId))
        .willReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                directMessageService.sendDirectMessage(
                    conversationId, senderId, new DirectMessageSendRequest("안녕하세요")))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

    verify(directMessageRepository, never()).save(any());
    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  @DisplayName("DM 읽음 처리 시 요청자 멤버의 lastReadAt을 DM 생성 시각으로 전진시키는 조건부 UPDATE를 호출한다")
  void markAsRead_advancesLastReadAt() {
    UUID conversationId = UUID.randomUUID();
    UUID directMessageId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();
    UUID memberId = UUID.randomUUID();

    Instant dmCreatedAt = Instant.parse("2026-07-02T00:00:00Z");
    ConversationMember requesterMember =
        mockConversationMember(requesterId, dmCreatedAt.minusSeconds(60));
    given(requesterMember.getId()).willReturn(memberId);
    DirectMessage dm =
        mockDirectMessage(conversationId, UUID.randomUUID(), requesterId, dmCreatedAt);

    given(conversationMemberRepository.findByConversationIdAndUserId(conversationId, requesterId))
        .willReturn(Optional.of(requesterMember));
    given(directMessageRepository.findByIdAndConversationId(directMessageId, conversationId))
        .willReturn(Optional.of(dm));

    directMessageService.markAsRead(conversationId, directMessageId, requesterId);

    verify(conversationMemberRepository).advanceLastReadAt(memberId, dmCreatedAt);
  }

  @Test
  @DisplayName("DM 읽음 처리 시 요청자가 대화방 멤버가 아니면 FORBIDDEN 예외가 발생한다")
  void markAsRead_notMember_throwsForbidden() {
    UUID conversationId = UUID.randomUUID();
    UUID directMessageId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();

    given(conversationMemberRepository.findByConversationIdAndUserId(conversationId, requesterId))
        .willReturn(Optional.empty());

    assertThatThrownBy(
            () -> directMessageService.markAsRead(conversationId, directMessageId, requesterId))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

    verify(directMessageRepository, never()).findByIdAndConversationId(any(), any());
  }

  @Test
  @DisplayName("DM 읽음 처리 시 해당 대화방에 DM이 없으면 DIRECT_MESSAGE_NOT_FOUND 예외가 발생한다")
  void markAsRead_dmNotFound_throwsNotFound() {
    UUID conversationId = UUID.randomUUID();
    UUID directMessageId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();

    ConversationMember requesterMember = mockConversationMember(requesterId, Instant.now());

    given(conversationMemberRepository.findByConversationIdAndUserId(conversationId, requesterId))
        .willReturn(Optional.of(requesterMember));
    given(directMessageRepository.findByIdAndConversationId(directMessageId, conversationId))
        .willReturn(Optional.empty());

    assertThatThrownBy(
            () -> directMessageService.markAsRead(conversationId, directMessageId, requesterId))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.DIRECT_MESSAGE_NOT_FOUND));

    verify(conversationMemberRepository, never()).advanceLastReadAt(any(), any());
  }

  // ──────────────────────────────────────────────
  // helpers
  // ──────────────────────────────────────────────

  private User mockUser(UUID id, String name, String email, String profileImageUrl) {
    User user = mock(User.class);
    given(user.getId()).willReturn(id);
    given(user.getName()).willReturn(name);
    given(user.getProfileImageUrl()).willReturn(profileImageUrl);
    return user;
  }

  private ConversationMember mockConversationMember(UUID userId, Instant lastReadAt) {
    ConversationMember member = mock(ConversationMember.class);
    given(member.getLastReadAt()).willReturn(lastReadAt);
    return member;
  }

  private ConversationMember mockConversationMemberWithUser(
      UUID userId, String name, String profileImageUrl) {
    User user = mockUser(userId, name, null, profileImageUrl);
    ConversationMember member = mock(ConversationMember.class);
    given(member.getUser()).willReturn(user);
    given(member.getLastReadAt()).willReturn(Instant.now());
    return member;
  }

  private ConversationMember mockConversationMemberWithConvId(
      UUID conversationId, UUID userId, String name, String profileImageUrl) {
    Conversation conversation = mock(Conversation.class);
    given(conversation.getId()).willReturn(conversationId);
    User user = name != null ? mockUser(userId, name, null, profileImageUrl) : mock(User.class);
    ConversationMember member = mock(ConversationMember.class);
    given(member.getConversation()).willReturn(conversation);
    given(member.getUser()).willReturn(user);
    given(member.getLastReadAt()).willReturn(Instant.now());
    return member;
  }

  private ConversationMember mockWithUserMember(
      UUID conversationId, UUID userId, String name, String profileImageUrl) {
    Conversation conversation = mock(Conversation.class);
    given(conversation.getId()).willReturn(conversationId);

    User user = mockUser(userId, name, null, profileImageUrl);
    ConversationMember member = mock(ConversationMember.class);
    given(member.getConversation()).willReturn(conversation);
    given(member.getUser()).willReturn(user);
    return member;
  }

  private DirectMessage mockDirectMessage(
      UUID conversationId, UUID senderId, UUID receiverId, Instant createdAt) {
    User senderUser = mockUser(senderId, "발신자", null, null);
    User receiverUser = mockUser(receiverId, "수신자", null, null);

    ConversationMember sender = mock(ConversationMember.class);
    given(sender.getUser()).willReturn(senderUser);

    ConversationMember receiver = mock(ConversationMember.class);
    given(receiver.getUser()).willReturn(receiverUser);

    Conversation conversation = mock(Conversation.class);
    given(conversation.getId()).willReturn(conversationId);

    DirectMessage dm = mock(DirectMessage.class);
    given(dm.getId()).willReturn(UUID.randomUUID());
    given(dm.getConversation()).willReturn(conversation);
    given(dm.getSender()).willReturn(sender);
    given(dm.getReceiver()).willReturn(receiver);
    given(dm.getContent()).willReturn("안녕하세요");
    given(dm.getCreatedAt()).willReturn(createdAt);
    return dm;
  }
}
