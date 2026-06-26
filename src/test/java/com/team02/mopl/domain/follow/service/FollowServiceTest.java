package com.team02.mopl.domain.follow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.team02.mopl.domain.follow.dto.FollowDto;
import com.team02.mopl.domain.follow.dto.FollowRequest;
import com.team02.mopl.domain.follow.entity.Follow;
import com.team02.mopl.domain.follow.repository.FollowRepository;
import com.team02.mopl.domain.notification.dto.NotificationCreateCommand;
import com.team02.mopl.domain.notification.service.NotificationService;
import com.team02.mopl.domain.user.entity.User;
import com.team02.mopl.domain.user.repository.UserRepository;
import com.team02.mopl.global.exception.BusinessException;
import com.team02.mopl.global.exception.ErrorCode;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FollowServiceTest {

  @Mock private FollowRepository followRepository;

  @Mock private UserRepository userRepository;

  @Mock private NotificationService notificationService;

  @InjectMocks private FollowService followService;

  private User mockUser(UUID id) {
    User user = mock(User.class);
    given(user.getId()).willReturn(id);
    return user;
  }

  private User mockUser(UUID id, String name) {
    User user = mockUser(id);
    given(user.getName()).willReturn(name);
    return user;
  }

  @Test
  @DisplayName("팔로우를 생성하고 FollowDto를 반환한다")
  void createFollow_success() {
    UUID followerId = UUID.randomUUID();
    UUID followeeId = UUID.randomUUID();
    User follower = mockUser(followerId, "팔로워");
    User followee = mockUser(followeeId);
    FollowRequest request = new FollowRequest(followeeId);

    given(userRepository.findByIdAndDeletedAtIsNull(followerId)).willReturn(Optional.of(follower));
    given(userRepository.findByIdAndDeletedAtIsNull(followeeId)).willReturn(Optional.of(followee));
    given(
            followRepository.existsByFollower_IdAndFollowee_IdAndDeletedAtIsNull(
                followerId, followeeId))
        .willReturn(false);
    given(followRepository.save(any(Follow.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    FollowDto result = followService.createFollow(followerId, request);

    assertThat(result.followerId()).isEqualTo(followerId);
    assertThat(result.followeeId()).isEqualTo(followeeId);

    verify(followRepository).save(any(Follow.class));
    verify(notificationService).createNotification(any(NotificationCreateCommand.class));
  }

  @Test
  @DisplayName("자기 자신을 팔로우하면 CANNOT_FOLLOW_SELF 예외가 발생한다")
  void createFollow_selfFollow_throwsException() {
    UUID userId = UUID.randomUUID();
    FollowRequest request = new FollowRequest(userId);

    assertThatThrownBy(() -> followService.createFollow(userId, request))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CANNOT_FOLLOW_SELF));

    verifyNoInteractions(userRepository, followRepository, notificationService);
  }

  @Test
  @DisplayName("팔로우 요청자가 존재하지 않으면 USER_NOT_FOUND 예외가 발생한다")
  void createFollow_followerNotFound_throwsException() {
    UUID followerId = UUID.randomUUID();
    UUID followeeId = UUID.randomUUID();
    FollowRequest request = new FollowRequest(followeeId);

    given(userRepository.findByIdAndDeletedAtIsNull(followerId)).willReturn(Optional.empty());

    assertThatThrownBy(() -> followService.createFollow(followerId, request))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND));
  }

  @Test
  @DisplayName("팔로우 대상 사용자가 존재하지 않으면 USER_NOT_FOUND 예외가 발생한다")
  void createFollow_followeeNotFound_throwsException() {
    UUID followerId = UUID.randomUUID();
    UUID followeeId = UUID.randomUUID();
    User follower = mock(User.class);
    FollowRequest request = new FollowRequest(followeeId);

    given(userRepository.findByIdAndDeletedAtIsNull(followerId)).willReturn(Optional.of(follower));
    given(userRepository.findByIdAndDeletedAtIsNull(followeeId)).willReturn(Optional.empty());

    assertThatThrownBy(() -> followService.createFollow(followerId, request))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND));
  }

  @Test
  @DisplayName("이미 팔로우 중이면 FOLLOW_ALREADY_EXISTS 예외가 발생한다")
  void createFollow_alreadyExists_throwsException() {
    UUID followerId = UUID.randomUUID();
    UUID followeeId = UUID.randomUUID();
    User follower = mock(User.class);
    User followee = mock(User.class);
    FollowRequest request = new FollowRequest(followeeId);

    given(userRepository.findByIdAndDeletedAtIsNull(followerId)).willReturn(Optional.of(follower));
    given(userRepository.findByIdAndDeletedAtIsNull(followeeId)).willReturn(Optional.of(followee));
    given(
            followRepository.existsByFollower_IdAndFollowee_IdAndDeletedAtIsNull(
                followerId, followeeId))
        .willReturn(true);

    assertThatThrownBy(() -> followService.createFollow(followerId, request))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FOLLOW_ALREADY_EXISTS));
  }

  @Test
  @DisplayName("본인이 생성한 팔로우를 취소하면 deletedAt이 설정된다")
  void cancelFollow_success() {
    UUID followerId = UUID.randomUUID();
    UUID followId = UUID.randomUUID();
    User follower = mockUser(followerId);
    User followee = mock(User.class);
    Follow follow = new Follow(follower, followee);

    given(followRepository.findByIdAndDeletedAtIsNull(followId)).willReturn(Optional.of(follow));

    followService.cancelFollow(followId, followerId);

    assertThat(follow.getDeletedAt()).isNotNull();
  }

  @Test
  @DisplayName("존재하지 않는 팔로우를 취소하면 FOLLOW_NOT_FOUND 예외가 발생한다")
  void cancelFollow_notFound_throwsException() {
    UUID requesterId = UUID.randomUUID();
    UUID followId = UUID.randomUUID();

    given(followRepository.findByIdAndDeletedAtIsNull(followId)).willReturn(Optional.empty());

    assertThatThrownBy(() -> followService.cancelFollow(followId, requesterId))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FOLLOW_NOT_FOUND));
  }

  @Test
  @DisplayName("다른 사용자의 팔로우를 취소하면 FOLLOW_FORBIDDEN 예외가 발생한다")
  void cancelFollow_differentOwner_throwsException() {
    UUID ownerId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();
    UUID followId = UUID.randomUUID();
    User owner = mockUser(ownerId);
    User followee = mock(User.class);
    Follow follow = new Follow(owner, followee);

    given(followRepository.findByIdAndDeletedAtIsNull(followId)).willReturn(Optional.of(follow));

    assertThatThrownBy(() -> followService.cancelFollow(followId, requesterId))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FOLLOW_FORBIDDEN));
  }

  @Test
  @DisplayName("내가 특정 사용자를 팔로우 중이면 FollowDto를 반환한다")
  void getFollowedByMe_success() {
    UUID followerId = UUID.randomUUID();
    UUID followeeId = UUID.randomUUID();
    User follower = mockUser(followerId);
    User followee = mockUser(followeeId);
    Follow follow = new Follow(follower, followee);

    given(userRepository.findByIdAndDeletedAtIsNull(followeeId)).willReturn(Optional.of(followee));
    given(
            followRepository.findByFollower_IdAndFollowee_IdAndDeletedAtIsNull(
                followerId, followeeId))
        .willReturn(Optional.of(follow));

    FollowDto result = followService.getFollowedByMe(followerId, followeeId);

    assertThat(result.followerId()).isEqualTo(followerId);
    assertThat(result.followeeId()).isEqualTo(followeeId);
  }

  @Test
  @DisplayName("팔로우 여부 조회 대상 사용자가 존재하지 않으면 USER_NOT_FOUND 예외가 발생한다")
  void getFollowedByMe_followeeNotFound_throwsException() {
    UUID followerId = UUID.randomUUID();
    UUID followeeId = UUID.randomUUID();

    given(userRepository.findByIdAndDeletedAtIsNull(followeeId)).willReturn(Optional.empty());

    assertThatThrownBy(() -> followService.getFollowedByMe(followerId, followeeId))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND));
  }

  @Test
  @DisplayName("내가 특정 사용자를 팔로우 중이 아니면 FOLLOW_NOT_FOUND 예외가 발생한다")
  void getFollowedByMe_notFound_throwsException() {
    UUID followerId = UUID.randomUUID();
    UUID followeeId = UUID.randomUUID();
    User followee = mock(User.class);

    given(userRepository.findByIdAndDeletedAtIsNull(followeeId)).willReturn(Optional.of(followee));
    given(
            followRepository.findByFollower_IdAndFollowee_IdAndDeletedAtIsNull(
                followerId, followeeId))
        .willReturn(Optional.empty());

    assertThatThrownBy(() -> followService.getFollowedByMe(followerId, followeeId))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FOLLOW_NOT_FOUND));
  }

  @Test
  @DisplayName("특정 사용자의 팔로워 수를 조회한다")
  void getFollowerCount_success() {
    UUID followeeId = UUID.randomUUID();
    User followee = mock(User.class);

    given(userRepository.findByIdAndDeletedAtIsNull(followeeId)).willReturn(Optional.of(followee));
    given(followRepository.countByFollowee_IdAndDeletedAtIsNull(followeeId)).willReturn(3L);

    long result = followService.getFollowerCount(followeeId);

    assertThat(result).isEqualTo(3L);
  }

  @Test
  @DisplayName("팔로워 수 조회 대상 사용자가 존재하지 않으면 USER_NOT_FOUND 예외가 발생한다")
  void getFollowerCount_followeeNotFound_throwsException() {
    UUID followeeId = UUID.randomUUID();

    given(userRepository.findByIdAndDeletedAtIsNull(followeeId)).willReturn(Optional.empty());

    assertThatThrownBy(() -> followService.getFollowerCount(followeeId))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND));
  }
}
