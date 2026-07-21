// 리뷰 조회 여정: 특정 콘텐츠의 리뷰 목록(커서) 조회.
//
// 대용량 더미(load-test/dummy) 전제 개조판:
//  - 콘텐츠를 매 iteration 편중 샘플링(head:mid:tail=20:30:50, lib/ids.pickReviewContent).
//    더미 리뷰는 콘텐츠별 Zipf(0.7)라, 전부 인기 콘텐츠만 때리면 버퍼 캐시 상주로 착시가 생긴다.
//  - VU 마다 서로 다른 더미 계정으로 로그인(캐시 편향 완화). 로그인은 VU 당 1회(setup-per-VU 없이
//    init→default 첫 진입에서 1회). access 토큰 10분이라 iteration 마다 로그인하지 않는다.
//  - 정렬 2종(createdAt/rating) 모두 인덱스가 있다(ix_reviews_content_created_id/_rating_id).
//
// 실행: k6 run -e BASE_URL=... -e CONFIG=smoke load-test/scenarios/review-read.js
//   사전: node load-test/gen-dummy-users.mjs (dummy-users.json 생성), 더미 데이터 적재.
//   Grafana 시각화(선택): 아래처럼 Prometheus remote-write 출력을 붙인다. 태그(head/mid/tail)가
//   그대로 라벨이 되어 대시보드에서 그룹별로 필터된다. 상세는 load-test/README.md "Grafana 시각화".
//     K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write \
//     K6_PROMETHEUS_RW_TREND_STATS="avg,min,max,p(90),p(95),p(99)" \
//     k6 run -o experimental-prometheus-rw --tag testid=review-read-$(date +%m%d-%H%M) \
//       -e BASE_URL=http://localhost:8080 -e CONFIG=load load-test/scenarios/review-read.js
import http from 'k6/http';
import { sleep } from 'k6';
import { authParams, BASE_URL, checkCursorResponse, fetchCursorPages } from '../lib/http.js';
import { login } from '../lib/auth.js';
import { pickDummyUser } from '../data/dummy-users.js';
import { pickReviewContent } from '../lib/ids.js';
import { optionsWith } from '../config/index.js';

// 태그별 SLO(초안, 1차 측정 후 조정). 그룹(head/mid/tail)별로 나눠 편중 격차를 본다.
export const options = optionsWith({
  'http_req_duration{name:reviews-list-head}': ['p(95)<500'],
  'http_req_duration{name:reviews-list-mid}': ['p(95)<500'],
  'http_req_duration{name:reviews-list-tail}': ['p(95)<500'],
  'http_req_duration{name:reviews-deep}': ['p(95)<800'],
});

// VU 별 1회 로그인 캐시. __VU 로 계정을 분할해 같은 유저 쏠림을 막는다.
let token = null;

function ensureToken() {
  if (token == null) {
    const user = pickDummyUser(__VU);
    token = login(user.email, user.password).accessToken;
  }
  return token;
}

export default function () {
  const accessToken = ensureToken();
  const { contentId, bucket } = pickReviewContent();

  // 첫 페이지: 정렬 2종을 번갈아. 그룹 태그로 head/mid/tail p95 를 분리 집계한다.
  const sortBy = Math.random() < 0.5 ? 'createdAt' : 'rating';
  const res = http.get(
    `${BASE_URL}/api/reviews?contentId=${contentId}&sortBy=${sortBy}&sortDirection=DESCENDING&limit=20`,
    authParams(accessToken, { tags: { name: `reviews-list-${bucket}` } })
  );
  checkCursorResponse(res, `reviews-list-${bucket}`);

  // head 콘텐츠(리뷰 수천~3.2만)에서만 깊은 페이지를 순회한다. count 가 매 페이지 반복되므로,
  // 페이지가 깊어질수록 응답이 우상향하면 totalCount 산출이 병목이라는 신호다.
  if (bucket === 'head') {
    fetchCursorPages(
      `${BASE_URL}/api/reviews?contentId=${contentId}&sortBy=createdAt&sortDirection=DESCENDING&limit=20`,
      accessToken,
      'reviews-deep'
    );
  }

  sleep(1);
}
