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
import com.team02.mopl.domain.dm.dto.DirectMessageDto;
import com.team02.mopl.domain.dm.dto.DirectMessageSendRequest;
import com.team02.mopl.domain.dm.entity.Conversation;
import com.team02.mopl.domain.dm.entity.ConversationMember;
import com.team02.mopl.domain.dm.entity.DirectMessage;
import com.team02.mopl.domain.dm.repository.ConversationMemberRepository;
import com.team02.mopl.domain.dm.repository.ConversationRepository;
import com.team02.mopl.domain.dm.repository.DirectMessageRepository;
import com.team02.mopl.domain.user.entity.User;
import com.team02.mopl.domain.user.repository.UserRepository;
import com.team02.mopl.global.exception.BusinessException;
import com.team02.mopl.global.exception.ErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DirectMessageServiceTest {

  @Mock private DirectMessageRepository directMessageRepository;
  @Mock private ConversationRepository conversationRepository;
  @Mock private ConversationMemberRepository conversationMemberRepository;
  @Mock private UserRepository userRepository;

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

    assertThat(result.conversationId()).isEqualTo(conversationId);
    assertThat(result.content()).isEqualTo("안녕하세요");
    assertThat(result.sender().userId()).isEqualTo(senderId);
    assertThat(result.receiver().userId()).isEqualTo(receiverId);
    verify(directMessageRepository).save(any(DirectMessage.class));
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
