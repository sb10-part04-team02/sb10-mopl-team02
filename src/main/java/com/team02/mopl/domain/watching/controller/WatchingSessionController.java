package com.team02.mopl.domain.watching.controller;

import com.team02.mopl.domain.watching.dto.WatchingSessionDto;
import com.team02.mopl.domain.watching.dto.WatchingSessionSearchRequest;
import com.team02.mopl.domain.watching.service.WatchingSessionService;
import com.team02.mopl.global.dto.CursorResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class WatchingSessionController implements WatchingSessionApi {

  private final WatchingSessionService watchingSessionService;

  @Override
  @GetMapping("/api/contents/{contentId}/watching-sessions")
  public ResponseEntity<CursorResponse<WatchingSessionDto>> getWatchingSessions(
      @PathVariable UUID contentId,
      @ParameterObject @ModelAttribute @Valid WatchingSessionSearchRequest request) {
    return ResponseEntity.ok(
        watchingSessionService.getWatchingSessionsByContent(contentId, request));
  }

  @Override
  @GetMapping("/api/users/{watcherId}/watching-sessions")
  public ResponseEntity<WatchingSessionDto> getWatchingSessionByWatcher(
      @PathVariable UUID watcherId) {
    return ResponseEntity.ok(watchingSessionService.getWatchingSessionByWatcher(watcherId));
  }
}
