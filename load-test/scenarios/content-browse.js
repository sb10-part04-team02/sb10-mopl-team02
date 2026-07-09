// 콘텐츠 조회 여정: 목록(커서) 조회 → 목록에서 얻은 id 로 상세 조회.
// 실행: k6 run -e BASE_URL=... -e CONFIG=smoke load-test/scenarios/content-browse.js
import http from 'k6/http';
import { sleep } from 'k6';
import { BASE_URL, authParams, checkCursorResponse } from '../lib/http.js';
import { login } from '../lib/auth.js';
import { pickUser } from '../data/users.js';

export { options } from '../config/index.js';

// setup 은 테스트 시작 시 1회 실행. 계정 하나로 토큰을 받아 전체 VU 가 공유한다.
// (access 토큰 10분 만료. 10분 넘는 실행이면 lib/auth.js 의 refresh() 를 VU 루프에 넣는다.)
export function setup() {
  const user = pickUser(0);
  const { accessToken } = login(user.email, user.password);
  return { accessToken };
}

export default function (data) {
  const params = authParams(data.accessToken, { tags: { name: 'contents-list' } });

  // 1) 콘텐츠 목록 (createdAt 내림차순, limit 20)
  const listRes = http.get(
    `${BASE_URL}/api/contents?sortBy=createdAt&sortDirection=DESCENDING&limit=20`,
    params
  );
  checkCursorResponse(listRes, 'contents-list');

  // 2) 목록에서 얻은 첫 콘텐츠로 상세 조회
  let ids = [];
  try {
    ids = (listRes.json('data') || []).map((c) => c.id);
  } catch (_) {
    ids = [];
  }
  if (ids.length > 0) {
    const detailParams = authParams(data.accessToken, { tags: { name: 'contents-detail' } });
    http.get(`${BASE_URL}/api/contents/${ids[0]}`, detailParams);
  }

  // --- 미구현: SPORT 타입 필터 시나리오 ---
  // sport(The Sports DB) 수집이 아직 미구현이라 SPORT 데이터가 DB 에 없다.
  // 수집 구현 후 아래 주석을 해제한다.
  // http.get(
  //   `${BASE_URL}/api/contents?typeEqual=SPORT&sortBy=watcherCount&sortDirection=DESCENDING&limit=20`,
  //   authParams(data.accessToken, { tags: { name: 'contents-sport' } })
  // );

  sleep(1);
}
