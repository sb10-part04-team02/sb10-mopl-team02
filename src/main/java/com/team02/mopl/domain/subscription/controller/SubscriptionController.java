package com.team02.mopl.domain.subscription.controller;

import com.team02.mopl.domain.subscription.service.SubscriptionService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/playlists/{playlistId}/subscription")
@RequiredArgsConstructor
public class SubscriptionController implements SubscriptionApi {

  private final SubscriptionService subscriptionService;

  @PostMapping
  public ResponseEntity<Void> subscribe(
      @PathVariable UUID playlistId, @AuthenticationPrincipal UUID requesterId) {
    subscriptionService.subscribe(playlistId, requesterId);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping
  public ResponseEntity<Void> unsubscribe(
      @PathVariable UUID playlistId, @AuthenticationPrincipal UUID requesterId) {
    subscriptionService.unsubscribe(playlistId, requesterId);
    return ResponseEntity.noContent().build();
  }
}
