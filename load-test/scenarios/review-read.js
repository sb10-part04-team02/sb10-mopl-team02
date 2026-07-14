// 리뷰 조회 여정: 콘텐츠별 리뷰 목록(커서 순회). 정렬을 createdAt/rating 으로 회전한다.
// review 조회는 contentId 기준이라 setup 에서 콘텐츠 id 풀을 확보하고, iteration 마다 랜덤으로 고른다.
// (콘텐츠 1개만 반복 조회하면 같은 인덱스 경로/버퍼만 때려 낙관 측정이 된다.)
// 실행: k6 run -e BASE_URL=... -e CONFIG=smoke load-test/scenarios/review-read.js
import { sleep } from 'k6';
import http from 'k6/http';
import { BASE_URL, authParams, fetchCursorPages } from '../lib/http.js';
import { login } from '../lib/auth.js';
import { getSession, assertAccountsCoverVus, seedEmail, ACCOUNT_OFFSET, SEED_PASSWORD } from '../lib/accounts.js';
import { optionsWith } from '../config/index.js';

const REVIEW_SORTS = ['createdAt', 'rating'];

export const options = optionsWith({
  'http_req_duration{name:reviews-list}': ['p(95)<500'],
});

export function setup() {
  assertAccountsCoverVus();
  const { accessToken } = login(seedEmail(ACCOUNT_OFFSET), SEED_PASSWORD);

  // 리뷰를 걸 콘텐츠 id 풀 확보(1개가 아니라 최대 100개).
  const res = http.get(
    `${BASE_URL}/api/contents?sortBy=createdAt&sortDirection=DESCENDING&limit=100`,
    authParams(accessToken)
  );
  let contentIds = [];
  try {
    contentIds = (res.json('data') || []).map((c) => c.id);
  } catch (_) {
    contentIds = [];
  }
  if (contentIds.length === 0) {
    throw new Error('콘텐츠가 없습니다. seed/bulk-seed.sql 시딩 또는 TMDB 수집으로 DB 를 먼저 채우세요.');
  }
  return { contentIds };
}

export default function (data) {
  const { accessToken } = getSession();
  const contentId = data.contentIds[Math.floor(Math.random() * data.contentIds.length)];
  const sortBy = REVIEW_SORTS[__ITER % REVIEW_SORTS.length];

  fetchCursorPages(
    `${BASE_URL}/api/reviews?contentId=${contentId}&sortBy=${sortBy}&sortDirection=DESCENDING&limit=20`,
    accessToken,
    'reviews-list'
  );

  sleep(1);
}
