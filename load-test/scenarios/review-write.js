// 리뷰 쓰기 부하: create → delete 사이클로 평점 재집계(refreshRatingAggregate) UPDATE 경합을 본다.
//
// 왜 이 시나리오가 필요한가 (구독 write 와의 차이)
//   리뷰 create/delete 는 매번 contents.average_rating/review_count 를 재집계하는 UPDATE 를 부른다.
//   구독의 subscriber_count 는 단순 +1/-1 이지만, 리뷰 재집계는 AVG/COUNT 서브쿼리까지 도는
//   더 무거운 UPDATE 다. 그래서 구독과 별도로 리뷰 쓰기 경합을 재는 값이 있다.
//
// 왜 create/delete 사이클인가
//   리뷰는 (author, content) 당 1개 제약(uk_reviews_user_content, deleted_at IS NULL 부분 유니크)이라
//   같은 계정으로 같은 콘텐츠에 반복 생성이 안 된다. 삭제는 soft delete 라 제약이 풀려,
//   삭제 직후 같은 조합으로 재생성할 수 있다. → iteration 마다 생성 후 즉시 삭제하면 무한 반복.
//
// soft delete 누적은 측정을 오염시키지 않는다 (#337 실측 근거)
//   재집계의 AVG/COUNT 는 deleted_at IS NULL 만 세고, ix_reviews_content_{created,rating}_id 가
//   `WHERE deleted_at IS NULL` 부분 인덱스라 죽은 행은 인덱스에서 빠진다. 재집계 비용은 "살아있는
//   행 수"가 결정하며, create/delete 사이클은 그 수를 일정하게 유지한다(누적은 디스크 용량 문제일 뿐).
//
// 두 모드 (-e MODE=hotspot|spread, 기본 hotspot) — subscribe-write.js 와 명칭 통일
//   hotspot: 전 VU 가 콘텐츠 소수(-e FOCUS=1)에 리뷰 집중 → 같은 contents 행 UPDATE 락 경합 최대.
//   spread : 넓게 분산(-e FOCUS=100) → 경합 없는 처리량 기준선.
//   두 모드의 create/delete p95 차이가 곧 재집계 락 경합 비용이다.
//
// 계정 (더미 데이터 전제)
//   VU 마다 다른 더미 계정으로 로그인해야 같은 콘텐츠에 서로 다른 author 로 동시 INSERT 가 일어난다.
//   한 계정을 공유하면 (author,content) 제약 때문에 409 로 튕겨 경합이 안 관찰된다.
//   pickDummyUser(USER_BASE + __VU) 로 VU 간 계정을 분할하고, 기존 더미 리뷰와의 충돌을 피하려
//   상위 대역(USER_BASE)에서 뽑는다. 더미 유저는 100000 명.
//
// 콘텐츠는 duuid 로 산술 생성한다(조회 불필요). 더미 콘텐츠는 1..20000.
//   hotspot 은 앞쪽(리뷰가 많은 인기 콘텐츠 = 재집계가 무거운 최악 케이스)을 고른다.
//
// 주의: access 토큰 10분. VUS×실행시간이 길면 401 위험(lib/auth.refresh 는 주석 처리).
//
// 실행:
//   node load-test/gen-dummy-users.mjs 100000
//   k6 run -e BASE_URL=http://localhost:8080 -e MODE=hotspot -e FOCUS=1 \
//     -e VUS=100 -e DURATION=2m load-test/scenarios/review-write.js
//   Grafana 시각화(선택): hotspot/spread 를 서로 다른 testid 로 돌려 대조. README "Grafana 시각화".
//     K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write \
//     K6_PROMETHEUS_RW_TREND_STATS="avg,min,max,p(90),p(95),p(99)" \
//     k6 run -o experimental-prometheus-rw --tag testid=review-write-hotspot-$(date +%m%d-%H%M) \
//       -e BASE_URL=http://localhost:8080 -e MODE=hotspot -e FOCUS=1 -e VUS=100 \
//       load-test/scenarios/review-write.js
//     # 대조군은 -e MODE=spread -e FOCUS=100 와 --tag testid=review-write-spread-... 로 한 번 더
import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import { BASE_URL, authParams } from '../lib/http.js';
import { login, fetchCsrfToken } from '../lib/auth.js';
import { pickDummyUser } from '../data/dummy-users.js';
import { CONTENT } from '../lib/ids.js';
import { writeThresholds } from '../config/write-thresholds.js';

const MODE = __ENV.MODE || 'hotspot'; // hotspot | spread
const VUS = Number(__ENV.VUS || 100);
const DURATION = __ENV.DURATION || '2m';
// 리뷰를 집중시킬 콘텐츠 개수. 기본은 모드에 따름(hotspot=1, spread=100). 명시하면 그 값을 쓴다.
const FOCUS = Number(__ENV.FOCUS || (MODE === 'spread' ? 100 : 1));
const USER_BASE = Number(__ENV.USER_BASE || 90000); // 계정 시작 오프셋(기존 더미 리뷰 회피)
// 콘텐츠 시작 오프셋. 앞쪽(1~) 콘텐츠는 기존 더미 리뷰가 최대 3.2만 건이라 90000+ 유저 상당수가
// 이미 리뷰를 갖고 있어 create 가 409 로 튕긴다. tail 쪽(리뷰 수십 건)을 hotspot 으로 잡아
// 기존 리뷰와의 (author,content) 충돌을 피하되, 소수 콘텐츠에 부하를 몰아 재집계 락은 유지한다.
const CONTENT_BASE = Number(__ENV.CONTENT_BASE || 19000);

export const options = {
  scenarios: {
    reviewWrite: {
      executor: 'constant-vus',
      vus: VUS,
      duration: DURATION,
    },
  },
  thresholds: writeThresholds,
};

// VU 별 1회 로그인. USER_BASE + __VU 로 계정 분할. CSRF 는 로테이트되므로 여기 캐시하지 않고
// 각 mutation 직전에 fetchCsrfToken() 으로 최신값을 읽는다.
let session = null;

function getSession() {
  if (session == null) {
    const user = pickDummyUser(USER_BASE + __VU);
    const { accessToken } = login(user.email, user.password);
    session = { accessToken };
  }
  return session;
}

// 이 iteration 이 리뷰를 걸 콘텐츠 번호. CONTENT_BASE 부터 FOCUS 개 중 하나를 무작위로.
function targetContentNo() {
  return CONTENT_BASE + Math.floor(Math.random() * FOCUS);
}

export function setup() {
  if (MODE !== 'hotspot' && MODE !== 'spread') {
    fail(`MODE 는 hotspot|spread 만 허용. 받은 값: ${MODE}`);
  }
  console.log(`리뷰 쓰기 부하: MODE=${MODE} VUS=${VUS} FOCUS=${FOCUS} USER_BASE=${USER_BASE}`);
}

export default function () {
  const { accessToken } = getSession();
  const contentId = CONTENT(targetContentNo());
  const createTags = { name: 'review-create', mode: MODE };

  // CSRF 토큰은 이 프로젝트에서 매 mutation 마다 로테이트된다(SpaCsrfTokenRequestHandler +
  // CookieCsrfTokenRepository). 따라서 요청 직전에 쿠키 jar 에서 최신값을 다시 읽어야 한다.
  // (세션 시작 시 1회 받은 값을 재사용하면 두 번째 mutation 부터 403 이 난다)
  const createCsrf = fetchCsrfToken();

  // 1) 리뷰 생성 → 평점 재집계 UPDATE 발생(경합 관찰 지점).
  const createRes = http.post(
    `${BASE_URL}/api/reviews`,
    JSON.stringify({
      contentId,
      text: `load test review VU ${__VU} iter ${__ITER}`,
      rating: Math.round(Math.random() * 50) / 10, // 0.0 ~ 5.0
    }),
    authParams(accessToken, {
      headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': createCsrf },
      tags: createTags,
      // 409(REVIEW_ALREADY_EXISTS)는 제약이 정상 동작한 것. 실패로 세지 않고 재시도한다.
      responseCallback: http.expectedStatuses(201, 409),
    })
  );
  check(createRes, {
    'review-create: 201 또는 409(중복 제약)': (r) => r.status === 201 || r.status === 409,
  });
  if (createRes.status !== 201) {
    // 이전 iteration 의 리뷰가 아직 살아있음 → 이번엔 건너뛰고 다음 iteration 에서 재시도.
    return;
  }

  let reviewId = null;
  try {
    reviewId = createRes.json('id');
  } catch (_) {
    reviewId = null;
  }
  if (!reviewId) {
    return;
  }

  // 2) 즉시 삭제 → 제약을 풀고(다음 iteration 재생성 가능) 재집계를 한 번 더 돌린다(경합 대상).
  //    create 가 CSRF 를 로테이트했으므로 delete 직전에 최신 토큰을 다시 읽는다.
  const deleteRes = http.del(
    `${BASE_URL}/api/reviews/${reviewId}`,
    null,
    authParams(accessToken, {
      headers: { 'X-XSRF-TOKEN': fetchCsrfToken() },
      tags: { name: 'review-delete' },
    })
  );
  check(deleteRes, { 'review-delete: 204': (r) => r.status === 204 });

  sleep(0.5);
}
