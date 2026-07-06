package com.team02.mopl.domain.notification.entity.enums;

public enum NotificationType {
  // 권한 변경
  ROLE_UPDATED,
  // 내 플레이리스트 구독
  PLAYLIST_SUBSCRIBED,
  // 구독중인 플레이리스트에 콘텐츠 추가되었을 때
  PLAYLIST_CONTENT_ADDED,
  // 팔로우한 사용자의 주요 활동 (플레이리스트 생성)
  FOLLOWING_USER_ACTIVITY,
  // 다른 사용자가 나를 팔로우
  USER_FOLLOWED,
  // DM 수신
  DIRECT_MESSAGE_RECEIVED
}
