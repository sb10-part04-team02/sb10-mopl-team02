package com.team02.mopl.domain.follow.service;

import com.team02.mopl.domain.follow.dto.FollowDto;
import com.team02.mopl.domain.follow.dto.FollowRequest;
import com.team02.mopl.domain.follow.entity.Follow;
import com.team02.mopl.domain.follow.event.FollowCreatedEvent;
import com.team02.mopl.domain.follow.exception.CannotFollowSelfException;
import com.team02.mopl.domain.follow.exception.FollowAlreadyExistsException;
import com.team02.mopl.domain.follow.exception.FollowForbiddenException;
import com.team02.mopl.domain.follow.exception.FollowNotFoundException;
import com.team02.mopl.domain.follow.exception.NotFollowedException;
import com.team02.mopl.domain.follow.repository.FollowRepository;
import com.team02.mopl.domain.user.entity.User;
import com.team02.mopl.domain.user.repository.UserRepository;
import com.team02.mopl.global.exception.BusinessException;
import com.team02.mopl.global.exception.ErrorCode;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
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
  private final ApplicationEventPublisher eventPublisher;

  // 특정 사용자를 팔로우하고, 팔로우 대상에게 알림을 생성한다.
  @Transactional
  public FollowDto createFollow(UUID followerId, @Valid FollowRequest request) {
    UUID followeeId = request.followeeId();

    validateNotSelfFollow(followerId, followeeId);

    User follower = getActiveUser(followerId);
    User followee = getActiveUser(followeeId);

    validateNotAlreadyFollowing(followerId, followeeId);

    Follow follow;
    try {
      follow = followRepository.save(new Follow(follower, followee));
    } catch (DataIntegrityViolationException e) {
      throw new FollowAlreadyExistsException();
    }

    eventPublisher.publishEvent(new FollowCreatedEvent(followerId, follower.getName(), followeeId));

    return FollowDto.from(follow);
  }

  // 요청자가 생성한 팔로우 관계를 취소한다.
  @Transactional
  public void cancelFollow(UUID followId, UUID requesterId) {
    Follow follow =
        followRepository
            .findByIdAndDeletedAtIsNull(followId)
            .orElseThrow(FollowNotFoundException::new);

    validateOwner(follow, requesterId);

    follow.delete();
  }

  // 로그인 사용자가 특정 사용자를 팔로우 중인지 조회한다.
  public FollowDto getFollowedByMe(UUID followerId, UUID followeeId) {
    getActiveUser(followeeId);

    Follow follow =
        followRepository
            .findByFollower_IdAndFollowee_IdAndDeletedAtIsNull(followerId, followeeId)
            .orElseThrow(() -> new NotFollowedException(followeeId, followerId));

    return FollowDto.from(follow);
  }

  // 특정 사용자를 팔로우하는 활성 팔로워 수를 조회한다.
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
      throw new CannotFollowSelfException();
    }
  }

  private void validateNotAlreadyFollowing(UUID followerId, UUID followeeId) {
    if (followRepository.existsByFollower_IdAndFollowee_IdAndDeletedAtIsNull(
        followerId, followeeId)) {
      throw new FollowAlreadyExistsException();
    }
  }

  private void validateOwner(Follow follow, UUID requesterId) {
    if (!follow.getFollower().getId().equals(requesterId)) {
      throw new FollowForbiddenException();
    }
  }
}
