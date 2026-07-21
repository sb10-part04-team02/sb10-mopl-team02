package com.team02.mopl.domain.content.ingestion.controller;

import com.team02.mopl.domain.content.enums.ContentSource;
import com.team02.mopl.domain.content.ingestion.ContentIngestionTrigger;
import com.team02.mopl.domain.content.ingestion.dto.IngestionTriggerRequest;
import com.team02.mopl.domain.content.ingestion.dto.IngestionTriggerResponse;
import jakarta.validation.Valid;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/contents/ingestion")
public class ContentIngestionController implements ContentIngestionApi {

  private final ContentIngestionTrigger contentIngestionTrigger;

  @Override
  @PreAuthorize("hasRole('ADMIN')")
  @PostMapping
  public ResponseEntity<IngestionTriggerResponse> triggerIngestion(
      @RequestBody(required = false) @Valid IngestionTriggerRequest request) {
    Set<ContentSource> sources =
        contentIngestionTrigger.trigger(request == null ? null : request.sources());
    return ResponseEntity.accepted().body(new IngestionTriggerResponse("콘텐츠 수집을 시작했습니다.", sources));
  }
}
