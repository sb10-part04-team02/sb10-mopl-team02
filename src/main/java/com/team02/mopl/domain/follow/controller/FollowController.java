package com.team02.mopl.domain.follow.controller;

import com.team02.mopl.domain.follow.dto.FollowDto;
import com.team02.mopl.domain.follow.dto.FollowRequest;
import com.team02.mopl.domain.follow.service.FollowService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/follows")
@RequiredArgsConstructor
public class FollowController implements FollowApi {

  private final FollowService followService;

  @Override
  @PostMapping
  public ResponseEntity<FollowDto> createFollow(
      @AuthenticationPrincipal UUID followerId, @RequestBody @Valid FollowRequest request) {
    // 요청자 ID는 클라이언트 입력이 아니라 인증 컨텍스트에서 가져옴
    FollowDto response = followService.createFollow(followerId, request);

    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @Override
  @GetMapping("/followed-by-me")
  public ResponseEntity<FollowDto> getFollowedByMe(
      @AuthenticationPrincipal UUID followerId, @RequestParam UUID followeeId) {
    // Swagger 명세상 팔로우 중이면 FollowDto, 아니면 서비스에서 404 예외.
    return ResponseEntity.ok(followService.getFollowedByMe(followerId, followeeId));
  }

  @Override
  @GetMapping("/count")
  public ResponseEntity<Long> getFollowerCount(@RequestParam UUID followeeId) {

    return ResponseEntity.ok(followService.getFollowerCount(followeeId));
  }

  @Override
  @DeleteMapping("/{followId}")
  public ResponseEntity<Void> cancelFollow(
      @AuthenticationPrincipal UUID requesterId, @PathVariable UUID followId) {

    followService.cancelFollow(followId, requesterId);

    return ResponseEntity.noContent().build();
  }
}
