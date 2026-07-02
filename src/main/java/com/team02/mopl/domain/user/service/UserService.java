package com.team02.mopl.domain.user.service;

import com.team02.mopl.domain.user.dto.UserCreateRequest;
import com.team02.mopl.domain.user.dto.UserDto;
import com.team02.mopl.domain.user.dto.UserUpdateRequest;
import com.team02.mopl.domain.user.entity.User;
import com.team02.mopl.domain.user.entity.enums.Role;
import com.team02.mopl.domain.user.exception.UserEmailDuplicateException;
import com.team02.mopl.domain.user.exception.UserForbiddenException;
import com.team02.mopl.domain.user.exception.UserNotFoundException;
import com.team02.mopl.domain.user.mapper.UserMapper;
import com.team02.mopl.domain.user.repository.UserRepository;
import com.team02.mopl.global.storage.FileStorage;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

  private final UserRepository userRepository;
  private final UserMapper userMapper;
  private final PasswordEncoder passwordEncoder;
  private final FileStorage fileStorage;

  @Transactional
  public UserDto createUser(UserCreateRequest request) {
    log.debug("유저 생성 시작: email={}", maskValidEmail(request.email()));

    if (userRepository.existsByEmailAndDeletedAtIsNull(request.email())) {
      throw new UserEmailDuplicateException();
    }

    String encryptedPassword = passwordEncoder.encode(request.password());
    // 광클이나 서비스, 네트워크 상황 대응으로의 2차 체크
    User savedUser;
    try {
      savedUser =
          userRepository.saveAndFlush(
              new User(request.name(), request.email(), encryptedPassword, null, Role.USER, false));
    } catch (DataIntegrityViolationException e) {
      throw new UserEmailDuplicateException();
    }
    UserDto userDto = userMapper.toDto(savedUser);

    log.info("유저 생성 성공: userId={}, email={}", userDto.id(), maskValidEmail(userDto.email()));
    return userDto;
  }

  public UserDto getUser(UUID userId) {
    User user =
        userRepository.findByIdAndDeletedAtIsNull(userId).orElseThrow(UserNotFoundException::new);

    return userMapper.toDto(user);
  }

  @Transactional
  public UserDto updateProfile(
      UUID requesterId, UUID userId, UserUpdateRequest request, MultipartFile image) {
    validateOwner(requesterId, userId);

    User user =
        userRepository.findByIdAndDeletedAtIsNull(userId).orElseThrow(UserNotFoundException::new);

    String profileImageUrl = user.getProfileImageUrl();
    if (image != null && !image.isEmpty()) {
      profileImageUrl = fileStorage.store(image);
    }

    user.updateProfile(request.name(), profileImageUrl);

    return userMapper.toDto(user);
  }

  private void validateOwner(UUID requesterId, UUID userId) {
    if (!userId.equals(requesterId)) {
      throw new UserForbiddenException();
    }
  }

  private String maskValidEmail(String validEmail) {
    String[] parts = validEmail.split("@");
    String local = parts[0];
    String domain = parts[1];

    if (local.length() <= 2) {
      return "**@" + domain;
    }

    // 앞글자 2글자만 공개
    return local.substring(0, 2) + "*".repeat(local.length() - 2) + "@" + domain;
  }
}
