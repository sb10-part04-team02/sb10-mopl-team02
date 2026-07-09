// 리뷰 쓰기 부하: create → delete 사이클로 평점 재집계(refreshRatingAggregate) UPDATE 경합을 본다.
//
// 왜 create/delete 사이클인가
//   리뷰는 (author, content) 당 1개 제약(existsByAuthorIdAndContentIdAndDeletedAtIsNull)이 있어
//   같은 계정으로 같은 콘텐츠에 반복 생성이 안 된다. 삭제는 soft delete 라 deletedAt 이 채워지고,
//   제약 검사도 deletedAt IS NULL 만 보므로 삭제 직후 같은 조합으로 재생성할 수 있다.
//   → iteration 마다 생성 후 즉시 삭제하면 무한 반복 가능.
//
// soft delete 누적에 대하여
//   삭제된 리뷰 행은 남는다(load 4분 30초 × 50 RPS 면 약 1.3만 행). 다만 이게 측정을 오염시키지는 않는다.
//   refreshRatingAggregate 의 AVG/COUNT 서브쿼리는 deleted_at IS NULL 만 세고,
//   ix_reviews_content_{created,rating}_id 가 `WHERE deleted_at IS NULL` 부분 인덱스라
//   죽은 행은 인덱스에서 아예 빠진다.
//   실측(PG16): 같은 콘텐츠에 살아있는 리뷰 50건 + 죽은 리뷰 20만 건일 때
//     Index Only Scan rows=50, buffers=4, 0.076ms  → 죽은 행 비용 0
//     반대로 살아있는 리뷰 5,000건이면 1.009ms (13배) → 비용은 "살아있는 행 수"가 결정한다.
//   create/delete 사이클은 살아있는 행 수를 일정하게 유지하므로 회차가 거듭돼도 재집계는 안 느려진다.
//   누적은 측정 유효성이 아니라 디스크 용량 문제다(힙은 커지고 부분 인덱스는 안 커진다).
//
// 경합 관찰 방법 (-e FOCUS_CONTENTS=N)
//   ReviewService.createReview/deleteReview 는 매번 ContentRatingService.refreshAggregate 를 호출하고,
//   이건 `UPDATE contents SET ... WHERE id = :contentId` 단일 UPDATE 다.
//   콘텐츠를 좁힐수록 같은 row 에 UPDATE 가 몰려 row-level lock 경합이 커진다.
//     FOCUS_CONTENTS=1   → 전 VU 가 한 콘텐츠에 집중(경합 최대)
//     FOCUS_CONTENTS=100 → 넓게 분산(경합 없는 처리량 기준선)
//   두 값으로 각각 돌려 p95 를 비교하는 게 이 시나리오의 목적이다.
//
//   상한 100 은 서버가 정한다. ContentSearchRequest.normalizedLimit() 이
//   Math.min(limit, MAX_LIMIT=100) 으로 clamp 하므로 limit 에 100 초과를 넣어도 100건만 온다.
//   분산 기준선은 100개로 충분하므로 커서로 여러 페이지를 긁지 않고 상한에서 멈춘다.
//
// 계정 (-e ACCOUNTS=N)
//   VU 마다 다른 계정으로 로그인해야 같은 콘텐츠에 서로 다른 author 로 동시 INSERT 가 일어난다.
//   한 계정을 공유하면 제약 때문에 동시 생성이 409 로 튕겨 경합이 관찰되지 않는다.
//   계정은 seed/bulk-seed.sql 이 만든 bulk{i}@mopl.test 를 인덱스로 쓴다.
//
//   ACCOUNTS 가 executor 의 maxVUs 보다 작으면 VU 인덱스가 한 바퀴 돌아 계정이 겹친다.
//   (stress 는 maxVUs=500, load 는 200) 겹치면 같은 계정이 같은 콘텐츠에 동시 생성을 시도해
//   409 가 나고, 그 iteration 은 delete 를 건너뛴다. create p95 는 낮게, delete 수는 적게 나와
//   측정이 통째로 왜곡된다. setup 에서 이 조건을 검사해 즉시 중단한다.
//
// 실행:
//   k6 run -e BASE_URL=http://localhost:8080 -e CONFIG=load -e FOCUS_CONTENTS=1 \
//     load-test/scenarios/review-write.js
//   # stress 는 maxVUs=500 이므로 계정도 500개 필요:
//   k6 run -e CONFIG=stress -e ACCOUNTS=500 ... (seed 의 user_count 도 500 이상)
import http from 'k6/http';
import exec from 'k6/execution';
import { check, fail } from 'k6';
import { BASE_URL, authParams } from '../lib/http.js';
import { login, fetchCsrfToken } from '../lib/auth.js';

export { options } from '../config/write-index.js';

// 서버의 ContentSearchRequest.MAX_LIMIT. 이보다 큰 limit 은 서버가 조용히 clamp 한다.
const SERVER_MAX_LIMIT = 100;

// 전 VU 가 리뷰를 집중시킬 콘텐츠 개수. 1 이면 경합 최대.
const FOCUS_CONTENTS = Number(__ENV.FOCUS_CONTENTS || 1);
// 로그인에 쓸 시딩 계정 수. seed/bulk-seed.sql 의 user_count 이하여야 한다
// (넘으면 없는 계정으로 로그인해 fail 한다).
const ACCOUNTS = Number(__ENV.ACCOUNTS || 200);
// seed/bulk-seed.sql 이 심은 공통 평문 비밀번호.
const SEED_PASSWORD = __ENV.SEED_PASSWORD || 'loadtest1234';

// 이 실행이 띄울 수 있는 최대 VU 수. arrival-rate 계열은 maxVUs, constant-vus 는 vus.
function plannedMaxVus() {
  const scenarios = exec.test.options.scenarios || {};
  let max = 0;
  for (const name in scenarios) {
    const s = scenarios[name];
    max = Math.max(max, Number(s.maxVUs || s.vus || 0));
  }
  return max;
}

// setup: 계정 겹침을 먼저 막고, 리뷰를 걸 콘텐츠 id 를 확보한다.
// 계정 하나로만 조회하면 되므로 여기서는 0번 계정을 쓴다.
export function setup() {
  // ACCOUNTS < maxVUs 이면 VU 인덱스가 한 바퀴 돌아 계정이 겹치고 측정이 왜곡된다.
  // 부하를 다 돌린 뒤에 알아차리면 늦으므로 시작 전에 중단한다.
  const maxVus = plannedMaxVus();
  if (maxVus > ACCOUNTS) {
    fail(
      `ACCOUNTS(${ACCOUNTS}) 가 maxVUs(${maxVus}) 보다 작습니다. ` +
        `VU 인덱스가 겹쳐 같은 계정이 같은 콘텐츠에 동시 리뷰를 시도하고, ` +
        `409 로 튕겨 delete 를 건너뛰어 create p95·delete 수가 모두 왜곡됩니다. ` +
        `-e ACCOUNTS=${maxVus} 이상으로 주고 seed 의 user_count 도 그만큼 확보하세요.`
    );
  }

  const { accessToken } = login(seedEmail(0), SEED_PASSWORD);

  // 서버가 limit 을 MAX_LIMIT(100)으로 clamp 하므로 그 이상은 요청해도 소용없다.
  const requestLimit = Math.min(FOCUS_CONTENTS, SERVER_MAX_LIMIT);
  if (FOCUS_CONTENTS > SERVER_MAX_LIMIT) {
    console.warn(
      `FOCUS_CONTENTS=${FOCUS_CONTENTS} 는 서버 상한(${SERVER_MAX_LIMIT})을 넘습니다. ${SERVER_MAX_LIMIT}개로 분산합니다.`
    );
  }

  const res = http.get(
    `${BASE_URL}/api/contents?sortBy=createdAt&sortDirection=DESCENDING&limit=${requestLimit}`,
    authParams(accessToken)
  );
  let contentIds = [];
  try {
    contentIds = (res.json('data') || []).map((c) => c.id);
  } catch (_) {
    contentIds = [];
  }
  if (contentIds.length === 0) {
    fail('콘텐츠가 없습니다. seed/bulk-seed.sql 또는 TMDB 수집으로 DB 를 먼저 시딩하세요.');
  }
  if (contentIds.length < requestLimit) {
    console.warn(
      `요청한 콘텐츠 ${requestLimit}개보다 DB 에 콘텐츠가 적습니다(${contentIds.length}개). 있는 만큼만 사용합니다.`
    );
  }
  return { contentIds };
}

function seedEmail(index) {
  return `bulk${index}@mopl.test`;
}

// VU 별 로그인은 iteration 마다가 아니라 최초 1회만 한다(BCrypt 비용이 측정치를 오염시킴).
// __VU 는 1부터 시작하므로 계정 인덱스로 쓰려면 -1 한다.
let session = null;

function getSession() {
  if (session === null) {
    const accountIndex = (__VU - 1) % ACCOUNTS;
    const { accessToken } = login(seedEmail(accountIndex), SEED_PASSWORD);
    // CSRF 토큰은 VU 쿠키 jar 기준으로 유효하므로 1회 받아 재사용한다.
    const csrf = fetchCsrfToken();
    if (!csrf) {
      fail('CSRF 토큰을 얻지 못했습니다.');
    }
    session = { accessToken, csrf };
  }
  return session;
}

export default function (data) {
  const { accessToken, csrf } = getSession();
  const contentId = data.contentIds[Math.floor(Math.random() * data.contentIds.length)];

  // 1) 리뷰 생성 → 평점 재집계 UPDATE 발생
  const createRes = http.post(
    `${BASE_URL}/api/reviews`,
    JSON.stringify({
      contentId,
      text: `load test review from VU ${__VU} iter ${__ITER}`,
      rating: Math.round(Math.random() * 50) / 10, // 0.0 ~ 5.0
    }),
    authParams(accessToken, {
      headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': csrf },
      tags: { name: 'review-create' },
      // 409(REVIEW_ALREADY_EXISTS)는 중복 제약이 정상 동작한 것이지 서버 장애가 아니다.
      // k6 기본값은 4xx 를 http_req_failed 로 세므로 2xx 와 409 를 성공으로 재정의한다.
      responseCallback: http.expectedStatuses(201, 409),
    })
  );

  const created = check(createRes, {
    'review-create: 201 또는 409(중복 제약)': (r) => r.status === 201 || r.status === 409,
  });
  if (!created || createRes.status !== 201) {
    // 409 면 이전 iteration 의 리뷰가 아직 남아 있다는 뜻. 다음 iteration 에서 재시도한다.
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

  // 2) 즉시 삭제 → 제약을 풀어 다음 iteration 이 같은 조합으로 다시 생성할 수 있게 한다.
  //    삭제도 평점 재집계를 한 번 더 돌린다(경합 관찰 대상).
  const deleteRes = http.del(
    `${BASE_URL}/api/reviews/${reviewId}`,
    null,
    authParams(accessToken, {
      headers: { 'X-XSRF-TOKEN': csrf },
      tags: { name: 'review-delete' },
    })
  );
  check(deleteRes, { 'review-delete: 204': (r) => r.status === 204 });
}
