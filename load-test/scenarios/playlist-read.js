// 플레이리스트 조회 여정: 목록(커서) 조회 → 상세 조회.
// 응답에 requester-relative subscribedByMe 가 포함된다.
//
// 대용량 더미(load-test/dummy) 전제 개조판: 경로별로 태그를 나눠 측정 대상을 넓힌다.
//  - sortBy=updatedAt / subscribeCount — 후자는 비정규화 컬럼 정렬. 둘 다 (정렬키,id) 복합
//    인덱스가 없어 순차 스캔+정렬이므로, 응답시간과 별개로 EXPLAIN 을 병행해야 한다(README 참고).
//  - subscriberIdEqual 필터 — EXISTS 서브쿼리 경로. 인덱스 선두가 user_id 라 이 조합이 인덱스를
//    타는지 확인 대상.
//  - 키워드 검색 유/무 — LIKE '%kw%' 순차 스캔. 3만 행이라 문제없을 것으로 예상하며 기준선 기록이 목적.
//    검색어는 더미 플리 제목 조합(40_social.sql)에서 뽑는다.
//  - VU 마다 서로 다른 더미 계정으로 로그인(subscribedByMe·subscriberIdEqual 을 의미있게 하려면
//    실제 구독을 가진 유저여야 한다).
//
// 실행: k6 run -e BASE_URL=... -e CONFIG=smoke load-test/scenarios/playlist-read.js
//   사전: node load-test/gen-dummy-users.mjs, 더미 데이터 적재.
//   Grafana 시각화(선택): 경로 태그(updated/subscribe/mine/search)가 라벨이 되어 대시보드에서
//   경로별로 필터된다. 상세는 load-test/README.md "Grafana 시각화".
//     K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write \
//     K6_PROMETHEUS_RW_TREND_STATS="avg,min,max,p(90),p(95),p(99)" \
//     k6 run -o experimental-prometheus-rw --tag testid=playlist-read-$(date +%m%d-%H%M) \
//       -e BASE_URL=http://localhost:8080 -e CONFIG=load load-test/scenarios/playlist-read.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import { authParams, BASE_URL, checkCursorResponse } from '../lib/http.js';
import { login } from '../lib/auth.js';
import { pickDummyUser } from '../data/dummy-users.js';
import { optionsWith } from '../config/index.js';

export const options = optionsWith({
  'http_req_duration{name:playlists-updated}': ['p(95)<500'],
  'http_req_duration{name:playlists-subscribe}': ['p(95)<500'],
  'http_req_duration{name:playlists-mine}': ['p(95)<500'],
  'http_req_duration{name:playlists-search}': ['p(95)<800'],
  'http_req_duration{name:playlists-detail}': ['p(95)<500'],
});

// 더미 플리 제목 첫 토큰(40_social.sql). 부분일치 검색어로 쓴다.
const KEYWORDS = ['정주행', '명작', '눈물', '입문', '주말', '심장'];

// VU 별 1회 로그인 캐시 + 그 유저 id(subscriberIdEqual 용).
let session = null;

function ensureSession() {
  if (session == null) {
    const user = pickDummyUser(__VU);
    session = login(user.email, user.password);
  }
  return session;
}

// 목록 1건 조회 + 커서 계약 검증. 첫 항목 id 를 반환(상세 조회용).
function listOnce(name, query, accessToken) {
  const res = http.get(
    `${BASE_URL}/api/playlists?${query}`,
    authParams(accessToken, { tags: { name } })
  );
  checkCursorResponse(res, name);
  check(res, {
    [`${name}: has subscribedByMe`]: (r) => {
      try {
        const data = r.json('data') || [];
        return data.length === 0 || typeof data[0].subscribedByMe === 'boolean';
      } catch (_) {
        return false;
      }
    },
  });
  try {
    return (res.json('data') || []).map((p) => p.id)[0] || null;
  } catch (_) {
    return null;
  }
}

export default function () {
  const { accessToken, userId } = ensureSession();

  // 1) 기본 정렬(updatedAt)
  const firstId = listOnce(
    'playlists-updated',
    'sortBy=updatedAt&sortDirection=DESCENDING&limit=20',
    accessToken
  );

  // 2) 구독수 정렬(비정규화 컬럼)
  listOnce(
    'playlists-subscribe',
    'sortBy=subscribeCount&sortDirection=DESCENDING&limit=20',
    accessToken
  );

  // 3) 내가 구독한 플리(EXISTS 서브쿼리 경로)
  listOnce(
    'playlists-mine',
    `subscriberIdEqual=${userId}&sortBy=updatedAt&sortDirection=DESCENDING&limit=20`,
    accessToken
  );

  // 4) 키워드 검색(LIKE '%kw%' 순차 스캔)
  const kw = KEYWORDS[Math.floor(Math.random() * KEYWORDS.length)];
  listOnce(
    'playlists-search',
    `keywordLike=${encodeURIComponent(kw)}&sortBy=updatedAt&sortDirection=DESCENDING&limit=20`,
    accessToken
  );

  // 상세 조회(목록 첫 항목)
  if (firstId) {
    const detailRes = http.get(
      `${BASE_URL}/api/playlists/${firstId}`,
      authParams(accessToken, { tags: { name: 'playlists-detail' } })
    );
    check(detailRes, { 'playlists-detail: status 200': (r) => r.status === 200 });
  }

  sleep(1);
}
