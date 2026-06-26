package com.team02.mopl.domain.follow.service;

import com.team02.mopl.domain.follow.dto.FollowDto;
import com.team02.mopl.domain.follow.dto.FollowRequest;
import com.team02.mopl.domain.follow.entity.Follow;
import com.team02.mopl.domain.follow.repository.FollowRepository;
import com.team02.mopl.domain.notification.dto.NotificationCreateCommand;
import com.team02.mopl.domain.notification.entity.enums.NotificationLevel;
import com.team02.mopl.domain.notification.entity.enums.NotificationType;
import com.team02.mopl.domain.notification.service.NotificationService;
import com.team02.mopl.domain.user.entity.User;
import com.team02.mopl.domain.user.repository.UserRepository;
import com.team02.mopl.global.exception.BusinessException;
import com.team02.mopl.global.exception.ErrorCode;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FollowService {

  private final FollowRepository followRepository;
  private final UserRepository userRepository;
  private final NotificationService notificationService;

  // 특정 사용자를 팔로우, 팔로우 대상에게 알림을 생성
  @Transactional
  public FollowDto createFollow(UUID followerId, @Valid FollowRequest request) {
    UUID followeeId = request.followeeId();

    validateNotSelfFollow(followerId, followeeId);

    User follower = getActiveUser(followerId);
    User followee = getActiveUser(followeeId);

    validateNotAlreadyFollowing(followerId, followeeId);

    Follow follow = followRepository.save(new Follow(follower, followee));

    notificationService.createNotification(
        new NotificationCreateCommand(
            followeeId,
            "새 팔로워 알림",
            follower.getName() + "님이 팔로우했습니다.",
            NotificationLevel.INFO,
            NotificationType.USER_FOLLOWED));

    return FollowDto.from(follow);
  }

  // 팔로우 취소
  @Transactional
  public void cancelFollow(UUID followId, UUID requesterId) {
    Follow follow =
        followRepository
            .findByIdAndDeletedAtIsNull(followId)
            .orElseThrow(() -> new BusinessException(ErrorCode.FOLLOW_NOT_FOUND));

    validateOwner(follow, requesterId);

    follow.delete();
  }

  // 특정 사용자를 팔로우 중인지 조회
  public FollowDto getFollowedByMe(UUID followerId, UUID followeeId) {
    getActiveUser(followeeId);

    Follow follow =
        followRepository
            .findByFollower_IdAndFollowee_IdAndDeletedAtIsNull(followerId, followeeId)
            .orElseThrow(() -> new BusinessException(ErrorCode.FOLLOW_NOT_FOUND));

    return FollowDto.from(follow);
  }

  public long getFollowerCount(UUID followeeId) {
    getActiveUser(followeeId);

    return followRepository.countByFollowee_IdAndDeletedAtIsNull(followeeId);
  }

  private User getActiveUser(UUID userId) {
    return userRepository
        .findByIdAndDeletedAtIsNull(userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
  }

  private void validateNotSelfFollow(UUID followerId, UUID followeeId) {
    if (followerId.equals(followeeId)) {
      throw new BusinessException(ErrorCode.CANNOT_FOLLOW_SELF);
    }
  }

  private void validateNotAlreadyFollowing(UUID followerId, UUID followeeId) {
    if (followRepository.existsByFollower_IdAndFollowee_IdAndDeletedAtIsNull(
        followerId, followeeId)) {
      throw new BusinessException(ErrorCode.FOLLOW_ALREADY_EXISTS);
    }
  }

  private void validateOwner(Follow follow, UUID requesterId) {
    if (!follow.getFollower().getId().equals(requesterId)) {
      throw new BusinessException(ErrorCode.FOLLOW_FORBIDDEN);
    }
  }
}
