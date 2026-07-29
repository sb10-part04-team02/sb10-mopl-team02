package com.team02.mopl.domain.contentchat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.team02.mopl.domain.content.entity.Content;
import com.team02.mopl.domain.content.exception.ContentNotFoundException;
import com.team02.mopl.domain.content.repository.ContentRepository;
import com.team02.mopl.domain.contentchat.dto.ContentChatDto;
import com.team02.mopl.domain.contentchat.dto.ContentChatSendRequest;
import com.team02.mopl.domain.contentchat.exception.NotWatchingContentException;
import com.team02.mopl.domain.user.entity.User;
import com.team02.mopl.domain.user.exception.UserNotFoundException;
import com.team02.mopl.domain.user.repository.UserRepository;
import com.team02.mopl.domain.watching.repository.WatchingSessionRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContentChatServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private ContentRepository contentRepository;
  @Mock private WatchingSessionRepository watchingSessionRepository;

  @InjectMocks private ContentChatService contentChatService;

  @Test
  @DisplayName("발신자 정보를 채워 ContentChatDto를 반환한다")
  void createMessage_success() {
    UUID contentId = UUID.randomUUID();
    UUID senderId = UUID.randomUUID();
    User sender = mockUser(senderId, "발신자", "https://img.example.com/profile.png");
    given(contentRepository.findByIdAndDeletedAtIsNull(contentId))
        .willReturn(Optional.of(mock(Content.class)));
    given(userRepository.findByIdAndDeletedAtIsNull(senderId)).willReturn(Optional.of(sender));
    given(
            watchingSessionRepository
                .existsByContent_IdAndUser_IdAndExitedAtIsNullAndDeletedAtIsNull(
                    contentId, senderId))
        .willReturn(true);

    ContentChatDto result =
        contentChatService.createMessage(contentId, senderId, new ContentChatSendRequest("안녕하세요"));

    assertThat(result.content()).isEqualTo("안녕하세요");
    assertThat(result.sender().userId()).isEqualTo(senderId);
    assertThat(result.sender().name()).isEqualTo("발신자");
    assertThat(result.sender().profileImageUrl()).isEqualTo("https://img.example.com/profile.png");
  }

  @Test
  @DisplayName("콘텐츠를 찾을 수 없으면 ContentNotFoundException을 던진다")
  void createMessage_contentNotFound() {
    UUID contentId = UUID.randomUUID();
    given(contentRepository.findByIdAndDeletedAtIsNull(contentId)).willReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                contentChatService.createMessage(
                    contentId, UUID.randomUUID(), new ContentChatSendRequest("안녕하세요")))
        .isInstanceOf(ContentNotFoundException.class);
  }

  @Test
  @DisplayName("발신자를 찾을 수 없으면 UserNotFoundException을 던진다")
  void createMessage_userNotFound() {
    UUID contentId = UUID.randomUUID();
    UUID senderId = UUID.randomUUID();
    given(contentRepository.findByIdAndDeletedAtIsNull(contentId))
        .willReturn(Optional.of(mock(Content.class)));
    given(userRepository.findByIdAndDeletedAtIsNull(senderId)).willReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                contentChatService.createMessage(
                    contentId, senderId, new ContentChatSendRequest("안녕하세요")))
        .isInstanceOf(UserNotFoundException.class);
  }

  @Test
  @DisplayName("발신자가 해당 콘텐츠의 활성 시청 세션에 참여 중이 아니면 NotWatchingContentException을 던진다")
  void createMessage_notWatching() {
    UUID contentId = UUID.randomUUID();
    UUID senderId = UUID.randomUUID();
    given(contentRepository.findByIdAndDeletedAtIsNull(contentId))
        .willReturn(Optional.of(mock(Content.class)));
    given(userRepository.findByIdAndDeletedAtIsNull(senderId))
        .willReturn(Optional.of(mock(User.class)));
    given(
            watchingSessionRepository
                .existsByContent_IdAndUser_IdAndExitedAtIsNullAndDeletedAtIsNull(
                    contentId, senderId))
        .willReturn(false);

    assertThatThrownBy(
            () ->
                contentChatService.createMessage(
                    contentId, senderId, new ContentChatSendRequest("안녕하세요")))
        .isInstanceOf(NotWatchingContentException.class);
  }

  private User mockUser(UUID id, String name, String profileImageUrl) {
    User user = mock(User.class);
    given(user.getId()).willReturn(id);
    given(user.getName()).willReturn(name);
    given(user.getProfileImageUrl()).willReturn(profileImageUrl);
    return user;
  }
}
