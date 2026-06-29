package com.team02.mopl.domain.dm.controller;

import com.team02.mopl.domain.dm.dto.ConversationCreateRequest;
import com.team02.mopl.domain.dm.dto.ConversationDto;
import com.team02.mopl.domain.dm.service.DirectMessageService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/conversations")
public class DirectMessageController implements DirectMessageApi {

  private final DirectMessageService directMessageService;

  @PostMapping
  public ResponseEntity<ConversationDto> createConversation(
      @AuthenticationPrincipal UUID userId, @RequestBody @Valid ConversationCreateRequest request) {
    return ResponseEntity.status(201)
        .body(directMessageService.createConversation(request, userId));
  }

  @GetMapping("/with")
  public ResponseEntity<ConversationDto> findConversationWith(
      @AuthenticationPrincipal UUID userId, @RequestParam UUID withUserId) {
    return ResponseEntity.ok(directMessageService.findConversationWith(userId, withUserId));
  }

  @GetMapping("/{conversationId}")
  public ResponseEntity<ConversationDto> findConversation(
      @AuthenticationPrincipal UUID userId, @PathVariable UUID conversationId) {
    return ResponseEntity.ok(directMessageService.findConversation(conversationId, userId));
  }
}
