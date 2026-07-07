package com.team02.mopl.domain.content.controller;

import com.team02.mopl.domain.content.dto.ContentCreateRequest;
import com.team02.mopl.domain.content.dto.ContentDto;
import com.team02.mopl.domain.content.dto.ContentSearchRequest;
import com.team02.mopl.domain.content.dto.ContentUpdateRequest;
import com.team02.mopl.domain.content.service.ContentService;
import com.team02.mopl.global.dto.CursorResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/contents")
public class ContentController implements ContentApi {

  private final ContentService contentService;

  @Override
  @PreAuthorize("hasRole('ADMIN')")
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ContentDto> createContent(
      @RequestPart("request") @Valid ContentCreateRequest request,
      @RequestPart(value = "thumbnail", required = false) MultipartFile thumbnail) {
    ContentDto created = contentService.create(request, thumbnail);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @Override
  @GetMapping("/{contentId}")
  public ResponseEntity<ContentDto> getContent(@PathVariable UUID contentId) {
    return ResponseEntity.ok(contentService.get(contentId));
  }

  @Override
  @GetMapping
  public ResponseEntity<CursorResponse<ContentDto>> getContents(
      @ParameterObject @ModelAttribute @Valid ContentSearchRequest request) {
    return ResponseEntity.ok(contentService.getContents(request));
  }

  @PreAuthorize("hasRole('ADMIN')")
  @Override
  @PatchMapping(value = "/{contentId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ContentDto> updateContent(
      @PathVariable UUID contentId,
      @RequestPart("request") @Valid ContentUpdateRequest request,
      @RequestPart(value = "thumbnail", required = false) MultipartFile thumbnail) {
    return ResponseEntity.ok(contentService.update(contentId, request, thumbnail));
  }

  @PreAuthorize("hasRole('ADMIN')")
  @Override
  @DeleteMapping("/{contentId}")
  public ResponseEntity<Void> deleteContent(@PathVariable UUID contentId) {
    contentService.delete(contentId);
    return ResponseEntity.noContent().build();
  }
}
