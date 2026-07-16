// 리뷰 조회 여정: 특정 콘텐츠의 리뷰 목록(커서) 조회.
// review 조회는 contentId 기준이라 setup 에서 콘텐츠 id 를 하나 확보한다.
// 실행: k6 run -e BASE_URL=... -e CONFIG=smoke load-test/scenarios/review-read.js
import http from 'k6/http';
import {fail, sleep} from 'k6';
import {authParams, BASE_URL, checkCursorResponse} from '../lib/http.js';
import {login} from '../lib/auth.js';
import {pickUser} from '../data/users.js';
import {optionsWith} from '../config/index.js';

// 태그별 SLO(초안, 1차 측정 후 조정)
export const options = optionsWith({
  'http_req_duration{name:reviews-list}': ['p(95)<500'],
});

export function setup() {
  const user = pickUser(0);
  const { accessToken } = login(user.email, user.password);

  // 리뷰를 걸 콘텐츠 id 하나 확보
  const res = http.get(`${BASE_URL}/api/contents?limit=1`, authParams(accessToken));
  let contentId = null;
  try {
    const data = res.json('data') || [];
    contentId = data.length > 0 ? data[0].id : null;
  } catch (_) {
    contentId = null;
  }
  if (!contentId) {
    fail('콘텐츠가 없습니다. TMDB 수집(run-on-startup=true)으로 DB 를 먼저 시딩하세요.');
  }
  return { accessToken, contentId };
}

export default function (data) {
  const params = authParams(data.accessToken, { tags: { name: 'reviews-list' } });
  const res = http.get(
    `${BASE_URL}/api/reviews?contentId=${data.contentId}&sortBy=createdAt&sortDirection=DESCENDING&limit=20`,
    params
  );
  checkCursorResponse(res, 'reviews-list');
  sleep(1);
}
