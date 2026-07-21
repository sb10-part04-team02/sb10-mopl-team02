// 구독 쓰기 부하: 구독 생성/취소의 동시성 특성을 잰다.
//
// 왜 이 시나리오가 따로 필요한가
//   #337 의 subscribe-notify.js 는 "콘텐츠 추가 팬아웃"을 재는 것이고 VU=1 직렬이라
//   구독 자체의 동시성을 안 밟는다. 여기서 재는 건 구독 생성 시 playlists.subscriber_count
//   원자적 UPDATE(PlaylistRepository.increaseSubscriberCount)가 같은 행에 걸리는 락 경합이다.
//
// 두 모드 (-e MODE=hotspot|spread, 기본 hotspot)
//   hotspot: 모든 VU 가 "인기 플리 1개"(-e HOT_PLAYLIST=<n>)에 동시 구독 → 같은 행 락 경합.
//   spread : 각 VU 가 서로 다른 플리에 구독 → 경합 없음. hotspot 의 대조군.
//   두 모드의 p95 차이가 곧 락 경합 비용이다. 대조 없이는 hotspot 숫자를 해석할 수 없다.
//
// 동시성 충돌 회피
//   같은 (user, playlist) 재구독은 409(SubscriptionAlreadyExistsException)다. 그래서
//     - VU 마다 서로 다른 더미 유저로 로그인(__VU 로 분할).
//     - 각 iteration 은 subscribe → (짧게) → unsubscribe 로 상태를 되돌려, 같은 유저가
//       다음 iteration 에 같은 플리를 다시 구독해도 409 가 안 나게 한다.
//   기존 더미 구독과의 충돌을 피하려고 유저는 상위 대역(-e USER_BASE=90000 이상)에서 뽑는다.
//     더미 유저는 100000 명이므로 90001..(90000+VUS) 를 쓴다. VUS 가 크면 USER_BASE 를 낮춘다.
//
// 카운트 정합성
//   subscribe→unsubscribe 가 짝이 맞으면 부하 후 subscriber_count 는 시작값으로 돌아와야 한다.
//   부하 종료 후 load-test/dummy/90_verify.sql 의 2번 섹션(subscriber_count_mismatch=0)으로 검증.
//   (원자적 +1/-1 이라 정상이면 0. 0 이 아니면 락/트랜잭션 버그 신호)
//
// 주의: access 토큰 10분. VUS×실행시간이 길면 401 위험(lib/auth.refresh 는 주석 처리).
//
// 실행:
//   node load-test/gen-dummy-users.mjs 100000        # dummy-users.json (상위 대역까지)
//   # HOT_PLAYLIST 는 90_verify.sql "구독자 수 상위 5" 에서 고른다.
//   k6 run -e BASE_URL=http://localhost:8080 -e MODE=hotspot -e HOT_PLAYLIST=1 \
//     -e VUS=100 -e DURATION=2m load-test/scenarios/subscribe-write.js
//   Grafana 시각화(선택): hotspot/spread 를 서로 다른 testid 로 돌리면 대시보드 Test ID 변수로
//   두 대조군을 나란히 비교할 수 있다(subscribe 태그의 mode 라벨로도 구분). README "Grafana 시각화".
//     K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write \
//     K6_PROMETHEUS_RW_TREND_STATS="avg,min,max,p(90),p(95),p(99)" \
//     k6 run -o experimental-prometheus-rw --tag testid=subscribe-hotspot-$(date +%m%d-%H%M) \
//       -e BASE_URL=http://localhost:8080 -e MODE=hotspot -e HOT_PLAYLIST=1 -e VUS=100 \
//       load-test/scenarios/subscribe-write.js
//     # 대조군은 -e MODE=spread 와 --tag testid=subscribe-spread-... 로 한 번 더
import http from 'k6/http';
import { fail, sleep } from 'k6';
import { BASE_URL, authParams } from '../lib/http.js';
import { login, fetchCsrfToken } from '../lib/auth.js';
import { pickDummyUser } from '../data/dummy-users.js';
import { PLAYLIST } from '../lib/ids.js';
import { writeThresholds } from '../config/write-thresholds.js';

const MODE = __ENV.MODE || 'hotspot'; // hotspot | spread
const VUS = Number(__ENV.VUS || 100);
const DURATION = __ENV.DURATION || '2m';
const HOT_PLAYLIST = Number(__ENV.HOT_PLAYLIST || 1); // hotspot 대상 플리 번호(1..30000)
const USER_BASE = Number(__ENV.USER_BASE || 90000); // 유저 뽑기 시작 오프셋(기존 더미 구독 회피)
const N_PLAYLISTS = Number(__ENV.N_PLAYLISTS || 30000); // spread 분산 상한(SCALE 에 맞춰 조정)

export const options = {
  scenarios: {
    subscribe: {
      executor: 'constant-vus',
      vus: VUS,
      duration: DURATION,
    },
  },
  thresholds: writeThresholds,
};

// VU 별 1회 로그인 캐시. USER_BASE + __VU 로 유저를 분할(VU 간 계정 겹침 없음).
let session = null;

function ensureSession() {
  if (session == null) {
    const user = pickDummyUser(USER_BASE + __VU);
    session = login(user.email, user.password);
  }
  return session;
}

// 이 VU 가 구독할 플리 번호. hotspot 은 전원 동일, spread 는 VU 마다 분산.
function targetPlaylistNo() {
  if (MODE === 'spread') {
    return 1 + ((USER_BASE + __VU) % N_PLAYLISTS);
  }
  return HOT_PLAYLIST;
}

export function setup() {
  if (MODE !== 'hotspot' && MODE !== 'spread') {
    fail(`MODE 는 hotspot|spread 만 허용. 받은 값: ${MODE}`);
  }
  console.log(
    `구독 쓰기 부하: MODE=${MODE} VUS=${VUS} ${MODE === 'hotspot' ? `HOT_PLAYLIST=${HOT_PLAYLIST}` : `분산 상한=${N_PLAYLISTS}`} USER_BASE=${USER_BASE}`
  );
}

export default function () {
  const { accessToken } = ensureSession();
  const playlistId = PLAYLIST(targetPlaylistNo());
  const subTags = { name: 'subscribe', mode: MODE };

  // CSRF 토큰은 매 mutation 마다 로테이트된다(SpaCsrfTokenRequestHandler). 요청 직전에
  // 쿠키 jar 에서 최신값을 읽는다. (subscribe 가 갱신한 토큰을 unsubscribe 가 그대로 쓰면 403)
  // 구독 생성 — 여기가 락 경합 측정 지점.
  const subRes = http.post(
    `${BASE_URL}/api/playlists/${playlistId}/subscription`,
    null,
    authParams(accessToken, {
      headers: { 'X-XSRF-TOKEN': fetchCsrfToken() },
      tags: subTags,
      // 204 신규. 400(SUBSCRIPTION_ALREADY_EXISTS)은 이미 구독(직전 unsubscribe 실패 등) —
      // 정상 흐름은 아니지만 실패로 세지 않고 아래에서 취소를 시도해 상태를 회복시킨다.
      responseCallback: http.expectedStatuses(204, 400),
    })
  );

  // 구독이 성립했거나 이미 있으면 되돌린다(다음 iteration 의 400 방지 + 카운트 짝 맞춤).
  if (subRes.status === 204 || subRes.status === 400) {
    sleep(0.1);
    http.del(
      `${BASE_URL}/api/playlists/${playlistId}/subscription`,
      null,
      authParams(accessToken, {
        headers: { 'X-XSRF-TOKEN': fetchCsrfToken() },
        tags: { name: 'unsubscribe' },
        // 204 취소. 404 는 이미 취소됨 — 정상.
        responseCallback: http.expectedStatuses(204, 404),
      })
    );
  } else {
    fail(`구독 실패 (status ${subRes.status}): ${String(subRes.body).slice(0, 200)}`);
  }

  sleep(0.5);
}
