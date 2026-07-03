package com.team02.mopl.domain.contentchat.service;

import com.team02.mopl.domain.contentchat.dto.ContentChatDto;
import com.team02.mopl.domain.contentchat.dto.ContentChatSendRequest;
import com.team02.mopl.domain.user.dto.UserSummary;
import com.team02.mopl.domain.user.entity.User;
import com.team02.mopl.domain.user.exception.UserNotFoundException;
import com.team02.mopl.domain.user.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ContentChatService {

  private final UserRepository userRepository;

  // 콘텐츠 채팅 메시지는 영속하지 않고(WebSocket 전용), 발신자 정보를 채워 전파용 DTO만 조립한다.
  @Transactional(readOnly = true)
  public ContentChatDto createMessage(UUID senderId, ContentChatSendRequest request) {
    User sender =
        userRepository.findByIdAndDeletedAtIsNull(senderId).orElseThrow(UserNotFoundException::new);

    UserSummary summary =
        new UserSummary(sender.getId(), sender.getName(), sender.getProfileImageUrl());

    return new ContentChatDto(summary, request.content());
  }
}
