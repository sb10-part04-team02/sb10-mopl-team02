// 팔로우 기반 알림 생성 파이프라인 부하테스트 시나리오.
//
// 검증 범위:
//   POST /api/follows
//     -> Follow 생성
//     -> USER_FOLLOWED Kafka 이벤트 발행/소비
//     -> Notification 저장
//     -> Redis Pub/Sub fan-out
//
// 실행:
//   k6 run -e BASE_URL=http://localhost:8080 -e CONFIG=smoke load-test/scenarios/follow-notification-pipeline.js
import http from 'k6/http';
import {check, sleep} from 'k6';
import {authParams, BASE_URL} from '../lib/http.js';
import {fetchCsrfToken, login} from '../lib/auth.js';
import {followeeUsers, followerUsers} from '../data/follow-pipeline-users.js';
import {optionsWith} from '../config/index.js';

export const options = optionsWith({
  'http_req_duration{name:follow-create}': ['p(95)<1000'],
  'http_req_duration{name:follow-delete}': ['p(95)<1000'],
});

export function setup() {
  // follower는 팔로우 요청을 보내는 사용자다.
  // 각 follower 계정의 accessToken을 미리 발급받아 VU들이 나눠 쓴다.
  const followers = followerUsers.map((user) => {
    const {accessToken, userId} = login(user.email, user.password);
    return {
      email: user.email,
      userId,
      accessToken,
    };
  });

  // followee는 팔로우 대상이며, USER_FOLLOWED 알림을 받는 사용자다.
  // POST /api/follows에는 followeeId가 필요하므로 로그인 응답에서 userId를 확보한다.
  const followees = followeeUsers.map((user) => {
    const {userId} = login(user.email, user.password);
    return {
      email: user.email,
      userId,
    };
  });

  if (followers.length === 0 || followees.length === 0) {
    throw new Error('팔로우 파이프라인 테스트 계정을 찾지 못했습니다.');
  }

  return {
    followers,
    followees,
  };
}

export default function (data) {
  // VU 번호 기준으로 follower를 분산한다.
  // 같은 VU 안에서는 iteration이 순차 실행되므로 같은 follower의 중복 요청 충돌을 줄일 수 있다.
  const follower = data.followers[(__VU - 1) % data.followers.length];

  // iteration마다 followee를 바꿔가며 팔로우 생성 대상이 한쪽으로 몰리지 않게 한다.
  const followee = data.followees[__ITER % data.followees.length];

  // 상태 변경 요청은 CSRF 보호 대상이므로, 현재 VU의 cookie jar에 XSRF-TOKEN을 발급받고 헤더에 실어 보낸다.
  const csrf = fetchCsrfToken();

  const createRes = http.post(
      `${BASE_URL}/api/follows`,
      JSON.stringify({followeeId: followee.userId}),
      authParams(follower.accessToken, {
        headers: {
          'Content-Type': 'application/json',
          'X-XSRF-TOKEN': csrf,
        },
        tags: {name: 'follow-create'},
      })
  );

  const created = check(createRes, {
    'follow-create: status 201': (r) => r.status === 201,
    'follow-create: has follow id': (r) => {
      try {
        return typeof r.json('id') === 'string';
      } catch (_) {
        return false;
      }
    },
  });

  if (!created) {
    // 중복 팔로우나 인증/CSRF 실패가 있으면 이후 정리 요청을 보내지 않는다.
    console.warn(
        `follow-create failed. status=${createRes.status}, follower=${follower.email}, followee=${followee.email}, body=${String(
            createRes.body).slice(0, 200)}`
    );
    sleep(1);
    return;
  }

  const followId = createRes.json('id');

  // 다음 iteration에서 같은 pair를 다시 사용할 수 있도록 생성한 follow는 바로 취소한다.
  // 알림은 이미 생성 파이프라인으로 넘어가므로 follow 정리와 별개로 Notification/Kafka/Redis 흐름을 검증할 수 있다.
  const deleteCsrf = fetchCsrfToken();

  const deleteRes = http.del(
      `${BASE_URL}/api/follows/${followId}`,
      null,
      authParams(follower.accessToken, {
        headers: {
          'X-XSRF-TOKEN': deleteCsrf,
        },
        tags: {name: 'follow-delete'},
      })
  );

  check(deleteRes, {
    'follow-delete: status 204': (r) => r.status === 204,
  });

  sleep(1);
}