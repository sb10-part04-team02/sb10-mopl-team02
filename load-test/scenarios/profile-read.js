// 프로필 방문 여정: 타인 프로필 단건 -> 팔로워 수 -> 내 팔로우 여부 -> 그 사람/콘텐츠의 시청 세션.
// 여러 소형 조회(단건/카운트/여부)를 한 여정에 묶어, 프로필 화면 한 번 여는 부하를 모사한다.
// 전제: seed/bulk-seed-social.sql(플레이리스트 소유자 = 타인 userId 풀, 팔로우 그래프).
// 실행: k6 run -e BASE_URL=... -e CONFIG=smoke load-test/scenarios/profile-read.js
import { sleep, check } from 'k6';
import http from 'k6/http';
import { BASE_URL, authParams } from '../lib/http.js';
import { login } from '../lib/auth.js';
import { getSession, assertAccountsCoverVus, seedEmail, ACCOUNT_OFFSET, SEED_PASSWORD } from '../lib/accounts.js';
import { optionsWith } from '../config/index.js';

export const options = optionsWith({
  'http_req_duration{name:user-detail}': ['p(95)<300'],
  'http_req_duration{name:follows-count}': ['p(95)<300'],
  'http_req_duration{name:follows-check}': ['p(95)<300'],
  'http_req_duration{name:watching-session-by-user}': ['p(95)<300'],
  'http_req_duration{name:watching-sessions-list}': ['p(95)<500'],
});

export function setup() {
  assertAccountsCoverVus();
  const { accessToken } = login(seedEmail(ACCOUNT_OFFSET), SEED_PASSWORD);

  // 방문 대상 userId 풀: 플레이리스트 소유자(owner.userId)를 끌어온다(소유자가 계정마다 달라 다양하다).
  const plRes = http.get(`${BASE_URL}/api/playlists?limit=100`, authParams(accessToken));
  let userIds = [];
  try {
    userIds = (plRes.json('data') || [])
      .map((p) => (p.owner ? p.owner.userId : null))
      .filter((id) => id != null);
  } catch (_) {
    userIds = [];
  }
  // 중복 제거
  userIds = Array.from(new Set(userIds));
  if (userIds.length === 0) {
    throw new Error('방문 대상 유저를 찾지 못했습니다. seed/bulk-seed-social.sql 로 소셜 데이터를 시딩하세요.');
  }

  // 콘텐츠별 시청 세션 조회용 콘텐츠 id 하나.
  const cRes = http.get(`${BASE_URL}/api/contents?limit=1`, authParams(accessToken));
  let contentId = null;
  try {
    const data = cRes.json('data') || [];
    contentId = data.length > 0 ? data[0].id : null;
  } catch (_) {
    contentId = null;
  }
  if (!contentId) {
    throw new Error('콘텐츠가 없습니다. seed/bulk-seed.sql 시딩 또는 TMDB 수집으로 DB 를 먼저 채우세요.');
  }
  return { userIds, contentId };
}

export default function (data) {
  const { accessToken } = getSession();
  const targetId = data.userIds[Math.floor(Math.random() * data.userIds.length)];

  // 1) 프로필 단건
  const userRes = http.get(
    `${BASE_URL}/api/users/${targetId}`,
    authParams(accessToken, { tags: { name: 'user-detail' } })
  );
  check(userRes, { 'user-detail: status 200': (r) => r.status === 200 });

  // 2) 팔로워 수(Long 반환)
  const countRes = http.get(
    `${BASE_URL}/api/follows/count?followeeId=${targetId}`,
    authParams(accessToken, { tags: { name: 'follows-count' } })
  );
  check(countRes, { 'follows-count: status 200': (r) => r.status === 200 });

  // 3) 내가 팔로우 중인지. 미팔로우면 서버가 404 를 주는 게 정상이므로 200/404 를 성공으로 처리한다.
  const checkRes = http.get(
    `${BASE_URL}/api/follows/followed-by-me?followeeId=${targetId}`,
    authParams(accessToken, {
      tags: { name: 'follows-check' },
      responseCallback: http.expectedStatuses(200, 404),
    })
  );
  check(checkRes, {
    'follows-check: 200 또는 404(미팔로우)': (r) => r.status === 200 || r.status === 404,
  });

  // 4) 그 사람이 지금 보고 있는 시청 세션(단건 또는 null)
  const watchByUserRes = http.get(
    `${BASE_URL}/api/users/${targetId}/watching-sessions`,
    authParams(accessToken, { tags: { name: 'watching-session-by-user' } })
  );
  check(watchByUserRes, { 'watching-session-by-user: status 200': (r) => r.status === 200 });

  // 5) 콘텐츠별 시청 세션 목록(시딩이 없으면 빈 목록 200 도 유효 경로)
  const watchListRes = http.get(
    `${BASE_URL}/api/contents/${data.contentId}/watching-sessions?sortDirection=DESCENDING&limit=20`,
    authParams(accessToken, { tags: { name: 'watching-sessions-list' } })
  );
  check(watchListRes, {
    'watching-sessions-list: status 200': (r) => r.status === 200,
    'watching-sessions-list: has data array': (r) => {
      try {
        return Array.isArray(r.json('data'));
      } catch (_) {
        return false;
      }
    },
  });

  sleep(1);
}
