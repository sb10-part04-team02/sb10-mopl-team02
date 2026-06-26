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
import com.team02.mopl.domain.dm.entity.Conversation;
import com.team02.mopl.domain.dm.entity.ConversationMember;
import com.team02.mopl.domain.dm.repository.ConversationMemberRepository;
import com.team02.mopl.domain.dm.repository.ConversationRepository;
import com.team02.mopl.domain.dm.repository.DirectMessageRepository;
import com.team02.mopl.domain.user.entity.User;
import com.team02.mopl.domain.user.repository.UserRepository;
import com.team02.mopl.global.exception.BusinessException;
import com.team02.mopl.global.exception.ErrorCode;
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
    assertThat(result.with().id()).isEqualTo(withUserId);
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

  private User mockUser(UUID id, String name, String email, String profileImageUrl) {
    User user = mock(User.class);
    given(user.getId()).willReturn(id);
    given(user.getName()).willReturn(name);
    given(user.getProfileImageUrl()).willReturn(profileImageUrl);
    return user;
  }
}
