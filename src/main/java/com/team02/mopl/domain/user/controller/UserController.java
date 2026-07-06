package com.team02.mopl.domain.user.controller;

import com.team02.mopl.domain.user.dto.UserCreateRequest;
import com.team02.mopl.domain.user.dto.UserDto;
import com.team02.mopl.domain.user.dto.UserUpdateRequest;
import com.team02.mopl.domain.user.service.UserService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController implements UserApi {

  private final UserService userService;

  @Override
  @PostMapping
  public ResponseEntity<UserDto> createUser(@RequestBody @Valid UserCreateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(userService.createUser(request));
  }

  @Override
  @GetMapping("/{userId}")
  public ResponseEntity<UserDto> getUser(@PathVariable UUID userId) {
    return ResponseEntity.ok(userService.getUser(userId));
  }

  @Override
  @PatchMapping(value = "/{userId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<UserDto> updateProfile(
      @AuthenticationPrincipal UUID requesterId,
      @PathVariable UUID userId,
      @RequestPart("request") @Valid UserUpdateRequest request,
      @RequestPart(value = "image", required = false) MultipartFile image) {
    return ResponseEntity.ok(userService.updateProfile(requesterId, userId, request, image));
  }
}
