// 팔로우 기반 알림 생성 파이프라인 부하테스트용 계정 데이터.
//
// follower 계정은 POST /api/follows 요청을 보내는 사용자다.
// followee 계정은 팔로우 대상이며, USER_FOLLOWED 알림을 받는 사용자다.
const PASSWORD = 'loadtest1234';

function range(count) {
  return Array.from({length: count}, (_, index) => index + 1);
}

export const followerUsers = range(80).map((n) => ({
  name: `follow-follower-${String(n).padStart(3, '0')}`,
  email: `follow-follower-${String(n).padStart(3, '0')}@mopl.test`,
  password: PASSWORD,
}));

export const followeeUsers = range(20).map((n) => ({
  name: `follow-followee-${String(n).padStart(3, '0')}`,
  email: `follow-followee-${String(n).padStart(3, '0')}@mopl.test`,
  password: PASSWORD,
}));

export const followPipelineUsers = [...followerUsers, ...followeeUsers];