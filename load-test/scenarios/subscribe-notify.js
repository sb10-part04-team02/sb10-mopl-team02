// 알림 팬아웃 부하: 구독자 N명이 붙은 플레이리스트에 콘텐츠를 추가해 알림 팬아웃 비용을 잰다.
//
// 팬아웃은 비동기가 아니라 동기다 (코드 확인 결과)
//   PlaylistContentAddedEventListener 는 @TransactionalEventListener(AFTER_COMMIT) 만 붙어 있고
//   @Async 가 없다. 코드베이스 어디에도 @EnableAsync 가 없다.
//   → 팬아웃은 별도 스레드풀이 아니라 "요청 스레드"에서 커밋 직후 동기로 실행된다.
//   → 리스너는 구독자 목록을 돌며 1명당 notificationService.createNotification() 을 호출하고,
//     그 안에서 receiver 조회 SELECT + notifications INSERT + SSE 전송이 일어난다.
//   결론: POST /api/playlists/{id}/contents/{contentId} 의 응답시간이 구독자 수에 선형 비례한다.
//   (이슈 #337·docs/k6-write-load-test-plan.md 의 "응답이 팬아웃을 기다리지 않는다" 는 서술은 사실과 다름)
//
//   리스너에는 @Transactional(propagation = REQUIRES_NEW) 도 함께 붙어 있다. 즉 원 요청 트랜잭션이
//   커밋된 뒤 요청 스레드가 DB 커넥션을 하나 더 잡고, 구독자 N명분 SELECT+INSERT 를
//   그 하나의 트랜잭션 안에서 전부 돈다.
//
// 이 시나리오가 재는 것 / 재지 않는 것
//   재는 것   : 팬아웃이 응답시간에 기여하는 몫 (구독자 수에 대한 선형 증가)
//   안 재는 것: HikariCP 커넥션 풀 고갈. 아래처럼 VU=1 직렬이라 동시 add 가 없어 그 경로를 안 밟는다.
//               실제 운영에서는 풀 고갈이 응답시간 증가보다 먼저 터질 수 있다.
//               측정하려면 플레이리스트를 여러 개 만들어 VU 를 늘리는 별도 시나리오가 필요하다.
//
// 측정 방법
//   add    → 팬아웃 있음 (구독자 N명분 SELECT+INSERT+SSE)
//   remove → 팬아웃 없음 (PlaylistService.removeContent 는 이벤트를 발행하지 않음)
//   같은 트랜잭션 골격에 팬아웃만 다르므로 두 태그의 p95 차이가 곧 팬아웃 비용이다.
//   -e SUBSCRIBERS 를 0 → 10 → 100 → 500 으로 올려가며 add 의 p95 가 어떻게 자라는지 본다.
//
// 왜 VU 를 늘리지 않는가
//   콘텐츠 추가는 소유자만 가능하고(getOwnedPlaylist → 비소유자 403),
//   같은 (playlist, content) 조합은 중복 추가가 안 된다(PlaylistContentAlreadyExistsException).
//   그래서 이 시나리오는 플레이리스트 1개 기준으로 본질적으로 직렬이다.
//   부하 변수는 VU 수가 아니라 "구독자 수"다. VU 는 1로 두고 SUBSCRIBERS 를 스윕한다.
//
// 주의: setup 시간과 access 토큰 10분 만료
//   setup 은 구독자 1명당 로그인(BCrypt strength 10, ~100ms+)을 태운다. SUBSCRIBERS=500 이면
//   setup 만 수십 초~수 분이다. 그런데 owner 토큰은 setup 최초에 1회 발급해 default·teardown 이
//   그대로 재사용하고, access-token-expiration 은 10m 다(application.yaml).
//   setup 시간 + DURATION 이 10분에 가까워지면 부하 도중 401 로 떨어진다.
//   lib/auth.js 의 refresh() 는 주석 처리되어 있어 대응 수단이 없으므로 아래에서 경고만 띄운다.
//
// 실행 (setup 이 구독자를 실제로 붙이므로 SUBSCRIBERS + 1 만큼 시딩 계정이 필요):
//   k6 run -e BASE_URL=http://localhost:8080 -e SUBSCRIBERS=100 \
//     load-test/scenarios/subscribe-notify.js
import http from 'k6/http';
import { check, fail } from 'k6';
import { BASE_URL, authParams } from '../lib/http.js';
import { login, fetchCsrfToken } from '../lib/auth.js';

import { writeThresholds } from '../config/write-thresholds.js';

// 이 시나리오는 구독자 수가 변수이므로 config/write-index.js 의 RPS 프로파일을 쓰지 않는다.
// 소유자 1명이 add/remove 를 반복하는 직렬 부하.
const DURATION = __ENV.DURATION || '1m';

export const options = {
  scenarios: {
    fanout: {
      executor: 'constant-vus',
      vus: 1,
      duration: DURATION,
    },
  },
  thresholds: writeThresholds,
};

// 플레이리스트에 붙일 구독자 수. 0 이면 팬아웃 없는 대조군.
// 계정 0번은 소유자로 쓰므로 seed/bulk-seed.sql 의 user_count 는 SUBSCRIBERS + 1 이상이어야 한다.
const SUBSCRIBERS = Number(__ENV.SUBSCRIBERS || 100);
const SEED_PASSWORD = __ENV.SEED_PASSWORD || 'loadtest1234';

// application.yaml 의 access-token-expiration: 10m
const TOKEN_TTL_SECONDS = 600;
// 이 정도 여유도 안 남으면 경고한다.
const TOKEN_SAFETY_MARGIN_SECONDS = 120;

function seedEmail(index) {
  return `bulk${index}@mopl.test`;
}

// setup: 소유자 계정으로 플레이리스트를 만들고, 구독자 SUBSCRIBERS 명을 실제로 구독시킨다.
// 콘텐츠 id 도 하나 확보한다(add/remove 를 반복할 대상).
export function setup() {
  const setupStart = Date.now();

  // 0번 계정을 플레이리스트 소유자로 쓴다. 소유자는 자기 플리를 구독할 수 없으므로
  // 구독자는 1번 계정부터 채운다(CannotSubscribeOwnPlaylistException).
  const owner = login(seedEmail(0), SEED_PASSWORD);
  const ownerCsrf = fetchCsrfToken();

  const createRes = http.post(
    `${BASE_URL}/api/playlists`,
    JSON.stringify({
      title: `fanout-load-${Date.now()}`,
      description: 'k6 알림 팬아웃 부하테스트용 플레이리스트',
    }),
    authParams(owner.accessToken, {
      headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': ownerCsrf },
      tags: { name: 'playlist-create' },
    })
  );
  if (createRes.status !== 201) {
    fail(`플레이리스트 생성 실패 (status ${createRes.status}): ${String(createRes.body).slice(0, 200)}`);
  }
  const playlistId = createRes.json('id');

  // add/remove 를 반복할 콘텐츠 하나 확보
  const contentRes = http.get(`${BASE_URL}/api/contents?limit=1`, authParams(owner.accessToken));
  let contentId = null;
  try {
    const data = contentRes.json('data') || [];
    contentId = data.length > 0 ? data[0].id : null;
  } catch (_) {
    contentId = null;
  }
  if (!contentId) {
    fail('콘텐츠가 없습니다. seed/bulk-seed.sql 또는 TMDB 수집으로 DB 를 먼저 시딩하세요.');
  }

  // 구독자 붙이기. setup 은 부하 측정 대상이 아니므로 여기 걸리는 시간은 결과에 안 잡힌다.
  // 구독자마다 로그인이 필요해 BCrypt 비용이 든다(SUBSCRIBERS 가 크면 setup 이 오래 걸림).
  let subscribed = 0;
  for (let i = 1; i <= SUBSCRIBERS; i++) {
    const sub = login(seedEmail(i), SEED_PASSWORD);
    const csrf = fetchCsrfToken();
    const res = http.post(
      `${BASE_URL}/api/playlists/${playlistId}/subscription`,
      null,
      authParams(sub.accessToken, {
        headers: { 'X-XSRF-TOKEN': csrf },
        tags: { name: 'subscribe' },
        // 400(SUBSCRIPTION_ALREADY_EXISTS)은 중복 구독 제약이 정상 동작한 것.
        responseCallback: http.expectedStatuses(204, 400),
      })
    );
    // 204 신규 구독, 400 이미 구독(재실행 시) 둘 다 "구독 상태"이므로 팬아웃 대상이다.
    // 그 외 상태는 구독이 성립하지 않은 것이므로 그냥 넘기면 안 된다.
    if (res.status === 204 || res.status === 400) {
      subscribed++;
    } else {
      fail(
        `구독 실패 (${seedEmail(i)}, status ${res.status}): ${String(res.body).slice(0, 200)}. ` +
          `구독자 수가 곧 이 시나리오의 부하 변수이므로 중단합니다.`
      );
    }
  }
  const setupSeconds = Math.round((Date.now() - setupStart) / 1000);

  // 요청한 구독자 수를 못 채우면 팬아웃 p95 가 다른 N 에 대한 값이 된다. 조용히 넘어가면 안 된다.
  if (subscribed !== SUBSCRIBERS) {
    fail(`구독자 ${SUBSCRIBERS}명을 요청했으나 ${subscribed}명만 구독되었습니다.`);
  }
  console.log(
    `팬아웃 대상 구독자 ${subscribed}명 준비 완료 (playlistId=${playlistId}, setup ${setupSeconds}초)`
  );

  // owner 토큰은 setup 에서 1회 발급해 default·teardown 이 재사용한다(access-token-expiration: 10m).
  // setup 에 이미 쓴 시간 + 부하 시간이 10분에 가까워지면 부하 도중 401 로 떨어진다.
  const budgetSeconds = TOKEN_TTL_SECONDS - setupSeconds;
  if (budgetSeconds < TOKEN_SAFETY_MARGIN_SECONDS) {
    console.warn(
      `setup 이 ${setupSeconds}초 걸려 owner 토큰 잔여 수명이 약 ${budgetSeconds}초입니다. ` +
        `DURATION(${DURATION}) 이 이보다 길면 부하 도중 401 이 납니다. ` +
        `SUBSCRIBERS 를 줄이거나 DURATION 을 줄이세요(lib/auth.js 의 refresh() 는 비활성).`
    );
  }

  return { accessToken: owner.accessToken, playlistId, contentId, subscribed };
}

// VU 별 CSRF 는 1회만 받는다.
let csrf = null;

export default function (data) {
  if (csrf === null) {
    csrf = fetchCsrfToken();
    if (!csrf) {
      fail('CSRF 토큰을 얻지 못했습니다.');
    }
  }

  // 1) 콘텐츠 추가 → 커밋 후 구독자 N명에게 동기 팬아웃
  const addRes = http.post(
    `${BASE_URL}/api/playlists/${data.playlistId}/contents/${data.contentId}`,
    null,
    authParams(data.accessToken, {
      headers: { 'X-XSRF-TOKEN': csrf },
      tags: { name: 'playlist-add-content' },
    })
  );
  check(addRes, { 'playlist-add-content: 204': (r) => r.status === 204 });

  // 2) 제거 → 팬아웃 없음(대조군). 다음 iteration 이 같은 콘텐츠를 다시 추가할 수 있게 한다.
  const removeRes = http.del(
    `${BASE_URL}/api/playlists/${data.playlistId}/contents/${data.contentId}`,
    null,
    authParams(data.accessToken, {
      headers: { 'X-XSRF-TOKEN': csrf },
      tags: { name: 'playlist-remove-content' },
    })
  );
  check(removeRes, { 'playlist-remove-content: 204': (r) => r.status === 204 });
}

// teardown: 부하로 만든 플레이리스트를 지운다.
//
// 주의 — notifications 는 정리되지 않고 순증한다.
//   add/remove 사이클이 순환시키는 건 playlist_contents 뿐이다(remove 가 하드 delete).
//   반면 add 는 매번 PlaylistContentAddedEvent 를 발행하고, remove 는 아무 이벤트도 발행하지 않는다.
//   즉 iteration 마다 구독자 N명분 notifications 행이 쌓이기만 한다.
//   SUBSCRIBERS=500 으로 1분만 돌려도 수만~수십만 행이다.
//   회차 간 비교를 하려면 회차마다 `docker compose down -v` 로 DB 를 초기화한다.
export function teardown(data) {
  const c = fetchCsrfToken();
  http.del(
    `${BASE_URL}/api/playlists/${data.playlistId}`,
    null,
    authParams(data.accessToken, {
      headers: { 'X-XSRF-TOKEN': c },
      tags: { name: 'playlist-delete' },
    })
  );
}
