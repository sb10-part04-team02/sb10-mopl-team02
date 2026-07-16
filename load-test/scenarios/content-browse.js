// 콘텐츠 목록 조회 여정: 목록 커서 순회 -> 키워드 검색 -> 타입 필터.
// 전부 같은 목록 엔드포인트(GET /api/contents)의 파라미터 변형
// 정렬을 iteration 마다 회전시켜(watcherCount/createdAt/rate) 서로 다른 인덱스 경로를 밟는다.
// 실행: k6 run -e BASE_URL=... -e CONFIG=smoke load-test/scenarios/content-browse.js
import http from 'k6/http';
import {sleep} from 'k6';
import {
  authParams,
  BASE_URL,
  checkCursorResponse,
  fetchCursorPages
} from '../lib/http.js';
import {login} from '../lib/auth.js';
import {pickUser} from '../data/users.js';
import {optionsWith} from '../config/index.js';

// 태그별 SLO(초안, 1차 측정 후 조정). keyword 검색은 선행 와일드카드 LIKE(순차 스캔)라 느슨하게.
export const options = optionsWith({
  'http_req_duration{name:contents-list}': ['p(95)<500'],
  'http_req_duration{name:contents-search}': ['p(95)<800'],
  'http_req_duration{name:contents-filter}': ['p(95)<500'],
});

// 콘텐츠 정렬 기준. watcherCount 는 sortBy 기본값이자 watching_sessions 실시간 집계
// 서브쿼리(ContentRepositoryImpl)라 대표 병목 후보다.
const CONTENT_SORTS = ['watcherCount', 'createdAt', 'rate'];

// 타입 필터 회전용. seed/seed-content-read.sql 이 3종 모두 시딩한다.
const CONTENT_TYPES = ['MOVIE', 'TV_SERIES', 'SPORT'];

// 시딩 title('loadtest movie N' 등)에 매칭되는 검색 키워드.
// keywordLike 는 선행 와일드카드 LIKE(순차 스캔)라 대표적 병목 후보(첫 페이지만 조회).
const SEARCH_KEYWORD = 'loadtest';

// setup 은 테스트 시작 시 1회 실행. 계정 하나로 토큰을 받아 전체 VU 가 공유한다.
// (k6 권장: 15분 미만 테스트는 setup 토큰 공유. 프로파일 최장 7분 < access 토큰 10분.
//  콘텐츠 목록 응답에는 요청자 기준 필드가 없어 계정을 나눌 이유도 없다.)
export function setup() {
  const user = pickUser(0);
  const { accessToken } = login(user.email, user.password);

  // 콘텐츠 자체가 없으면 목록/검색/필터 전부 빈 결과라 부하 의미가 없다. 시작 전에 중단한다.
  const listRes = http.get(`${BASE_URL}/api/contents?limit=1`, authParams(accessToken));
  let hasContent = false;
  try {
    hasContent = (listRes.json('data') || []).length > 0;
  } catch (_) {
    hasContent = false;
  }
  if (!hasContent) {
    throw new Error('콘텐츠가 없습니다. load-test/seed/seed-content-read.sql 로 DB 를 먼저 채우세요.');
  }

  return { accessToken };
}

export default function (data) {
  const sortBy = CONTENT_SORTS[__ITER % CONTENT_SORTS.length];

  // 1) 목록 커서 순회 (정렬 회전, 최대 MAX_PAGES 페이지)
  // 정렬 기준으로 목록을 여러 페이지 넘어가며 조회.
  // 정렬은 iteration마다 watcherCount -> createdAt -> rate로 회전
  fetchCursorPages(
    `${BASE_URL}/api/contents?sortBy=${sortBy}&sortDirection=DESCENDING&limit=20`,
    data.accessToken,
    'contents-list'
  );

  // 2) 키워드 검색 (첫 페이지만) - loadtest로 검색
  // 검색이나 필터를 걸면 사용자는 보통 첫 페이지만 보고 원하는 것을 찾거나 다른 조건으로 바꾸므로 첫페이만
  const searchRes = http.get(
    `${BASE_URL}/api/contents?keywordLike=${SEARCH_KEYWORD}&sortBy=createdAt&sortDirection=DESCENDING&limit=20`,
    authParams(data.accessToken, { tags: { name: 'contents-search' } })
  );
  checkCursorResponse(searchRes, 'contents-search');

  // 3) 타입 필터 (회전, 첫 페이지만) - MOVIE/TV_SERIES/SPORT를 회전하며 필터
  const typeEqual = CONTENT_TYPES[__ITER % CONTENT_TYPES.length];
  const filterRes = http.get(
    `${BASE_URL}/api/contents?typeEqual=${typeEqual}&sortBy=watcherCount&sortDirection=DESCENDING&limit=20`,
    authParams(data.accessToken, { tags: { name: 'contents-filter' } })
  );
  checkCursorResponse(filterRes, 'contents-filter');

  sleep(1);
}
