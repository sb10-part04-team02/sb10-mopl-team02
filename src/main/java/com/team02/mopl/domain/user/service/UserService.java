package com.team02.mopl.domain.user.service;

import com.team02.mopl.domain.user.dto.UserCreateRequest;
import com.team02.mopl.domain.user.dto.UserDto;
import com.team02.mopl.domain.user.dto.UserSearchRequest;
import com.team02.mopl.domain.user.dto.UserUpdateRequest;
import com.team02.mopl.domain.user.entity.User;
import com.team02.mopl.domain.user.entity.enums.Role;
import com.team02.mopl.domain.user.enums.UserSortBy;
import com.team02.mopl.domain.user.exception.UserEmailDuplicateException;
import com.team02.mopl.domain.user.exception.UserForbiddenException;
import com.team02.mopl.domain.user.exception.UserNotFoundException;
import com.team02.mopl.domain.user.mapper.UserMapper;
import com.team02.mopl.domain.user.repository.UserRepository;
import com.team02.mopl.global.dto.CursorPageRequest;
import com.team02.mopl.global.dto.CursorResponse;
import com.team02.mopl.global.enums.SortDirection;
import java.util.List;
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

  public CursorResponse<UserDto> getUsers(UserSearchRequest request) {
    int limit = CursorPageRequest.normalizeLimit(request.limit());
    SortDirection direction =
        request.sortDirection() != null ? request.sortDirection() : SortDirection.ASCENDING;
    UserSortBy sortBy = request.sortBy() != null ? request.sortBy() : UserSortBy.NAME;

    List<User> users =
        userRepository.findUsersByCursor(
            request.emailLike(),
            request.roleEqual(),
            request.isLocked(),
            request.cursor(),
            request.idAfter(),
            limit + 1,
            direction,
            sortBy);

    boolean hasNext = users.size() > limit;
    List<User> page = hasNext ? users.subList(0, limit) : users;

    List<UserDto> data = page.stream().map(userMapper::toDto).toList();
    long totalCount = userRepository.countByDeletedAtIsNull();

    String nextCursor = null;
    UUID nextIdAfter = null;
    if (hasNext) {
      User last = page.get(page.size() - 1);
      nextCursor = encodeCursor(sortBy, last);
      nextIdAfter = last.getId();
    }

    return new CursorResponse<>(
        data, nextCursor, nextIdAfter, hasNext, totalCount, sortBy.getValue(), direction.name());
  }

  private String encodeCursor(UserSortBy sortBy, User user) {
    return switch (sortBy) {
      case NAME -> user.getName();
      case EMAIL -> user.getEmail();
      case CREATED_AT -> user.getCreatedAt().toString();
      case IS_LOCKED -> Boolean.toString(user.isLocked());
      case ROLE -> user.getRole().name();
    };
  }

  @Transactional
  public UserDto updateProfile(
      UUID requesterId, UUID userId, UserUpdateRequest request, MultipartFile image) {
    validateOwner(requesterId, userId);

    User user =
        userRepository.findByIdAndDeletedAtIsNull(userId).orElseThrow(UserNotFoundException::new);

    // TODO: 이미지 저장소 연동 후 image가 있으면 저장된 URL로 교체
    // 현재는 이름만 수정하고 기존 프로필 이미지 URL을 유지
    user.updateProfile(request.name(), user.getProfileImageUrl());

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
