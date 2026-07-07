package com.team02.mopl.domain.user.service;

import com.team02.mopl.domain.user.dto.UserCreateRequest;
import com.team02.mopl.domain.user.dto.UserDto;
import com.team02.mopl.domain.user.dto.UserRoleUpdateRequest;
import com.team02.mopl.domain.user.dto.UserSearchRequest;
import com.team02.mopl.domain.user.dto.UserUpdateRequest;
import com.team02.mopl.domain.user.entity.User;
import com.team02.mopl.domain.user.entity.enums.Role;
import com.team02.mopl.domain.user.enums.UserSortBy;
import com.team02.mopl.domain.user.event.RoleUpdatedEvent;
import com.team02.mopl.domain.user.exception.UserEmailDuplicateException;
import com.team02.mopl.domain.user.exception.UserForbiddenException;
import com.team02.mopl.domain.user.exception.UserInvalidProfileImageException;
import com.team02.mopl.domain.user.exception.UserNotFoundException;
import com.team02.mopl.domain.user.mapper.UserMapper;
import com.team02.mopl.domain.user.repository.UserRepository;
import com.team02.mopl.domain.user.util.UserCursorConverter;
import com.team02.mopl.global.dto.CursorPageRequest;
import com.team02.mopl.global.dto.CursorResponse;
import com.team02.mopl.global.enums.SortDirection;
import com.team02.mopl.global.exception.BusinessException;
import com.team02.mopl.global.exception.ErrorCode;
import com.team02.mopl.global.storage.FileStorage;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
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
  private final ApplicationEventPublisher eventPublisher;

  // 이미지 검증용
  private static final long MAX_PROFILE_IMAGE_SIZE = 5 * 1024 * 1024;
  private static final List<String> ALLOWED_PROFILE_IMAGE_CONTENT_TYPES =
      List.of("image/jpeg", "image/png", "image/webp");

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
      throw new UserEmailDuplicateException(e);
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

    if (!CursorPageRequest.isValidCursorCombo(request.cursor(), request.idAfter())) {
      throw new BusinessException(ErrorCode.INVALID_REQUEST);
    }

    Comparable<?> cursor = UserCursorConverter.toSortKey(sortBy, request.cursor());

    // hasNext 판정을 위해 limit + 1건을 조회
    List<User> users =
        userRepository.findUsersByCursor(
            request.emailLike(),
            request.roleEqual(),
            request.isLocked(),
            cursor,
            request.idAfter(),
            limit + 1,
            direction,
            sortBy);

    boolean hasNext = users.size() > limit;
    List<User> page = hasNext ? users.subList(0, limit) : users;

    List<UserDto> data = page.stream().map(userMapper::toDto).toList();
    long totalCount =
        userRepository.countUsersByCursor(
            request.emailLike(), request.roleEqual(), request.isLocked());

    String nextCursor = null;
    UUID nextIdAfter = null;
    if (hasNext) {
      User last = page.get(page.size() - 1);
      nextCursor = UserCursorConverter.toCursor(sortBy, last);
      nextIdAfter = last.getId();
    }

    return new CursorResponse<>(
        data, nextCursor, nextIdAfter, hasNext, totalCount, sortBy.getValue(), direction.name());
  }

  @Transactional
  public UserDto updateProfile(
      UUID requesterId, UUID userId, UserUpdateRequest request, MultipartFile image) {
    validateOwner(requesterId, userId);

    User user =
        userRepository.findByIdAndDeletedAtIsNull(userId).orElseThrow(UserNotFoundException::new);

    String oldProfileImageUrl = user.getProfileImageUrl();
    String profileImageUrl = oldProfileImageUrl;

    if (image != null && !image.isEmpty()) {
      validateProfileImage(image);
      profileImageUrl = fileStorage.store(image);
    }

    user.updateProfile(request.name(), profileImageUrl);
    UserDto userDto = userMapper.toDto(user);

    deleteOldProfileImageIfReplaced(oldProfileImageUrl, profileImageUrl);

    return userDto;
  }

  @Transactional
  public void updateRole(UUID userId, UserRoleUpdateRequest request) {
    log.debug("유저 권한변경 시작: userId={}", userId);
    User findUser =
        userRepository.findByIdAndDeletedAtIsNull(userId).orElseThrow(UserNotFoundException::new);

    Role newRole = request.role();
    Role oldRole = findUser.updateRole(newRole);

    eventPublisher.publishEvent(new RoleUpdatedEvent(userId, oldRole, newRole));

    log.info("유저 권한변경 로직 완료: userId={}, role=[{} -> {}]", findUser.getId(), oldRole, newRole);
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

  private void validateProfileImage(MultipartFile image) {
    if (image.getSize() > MAX_PROFILE_IMAGE_SIZE) {
      throw new UserInvalidProfileImageException();
    }

    String contentType = image.getContentType();
    if (!StringUtils.hasText(contentType)
        || !ALLOWED_PROFILE_IMAGE_CONTENT_TYPES.contains(contentType)) {
      throw new UserInvalidProfileImageException();
    }
  }

  private void deleteOldProfileImageIfReplaced(
      String oldProfileImageUrl, String newProfileImageUrl) {
    if (oldProfileImageUrl == null || oldProfileImageUrl.equals(newProfileImageUrl)) {
      return;
    }

    try {
      fileStorage.delete(oldProfileImageUrl);
    } catch (RuntimeException e) {
      log.warn("기존 프로필 이미지 삭제 실패. profileImageUrl={}", oldProfileImageUrl, e);
    }
  }
}
