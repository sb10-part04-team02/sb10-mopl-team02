// 콘텐츠 조회 여정: 목록(커서 순회) -> 상세 -> 키워드 검색 -> SPORT 필터.
// 정렬을 iteration 마다 회전시켜(watcherCount/createdAt/rate) 서로 다른 인덱스 경로를 밟는다.
// 실행: k6 run -e BASE_URL=... -e CONFIG=smoke load-test/scenarios/content-browse.js
import http from 'k6/http';
import { sleep, check } from 'k6';
import { BASE_URL, authParams, checkCursorResponse, fetchCursorPages } from '../lib/http.js';
import { login } from '../lib/auth.js';
import { getSession, assertAccountsCoverVus, seedEmail, ACCOUNT_OFFSET, SEED_PASSWORD } from '../lib/accounts.js';
import { optionsWith } from '../config/index.js';

// 콘텐츠 정렬 기준. watcherCount 는 sortBy 기본값이자 실시간 count 서브쿼리(ContentRepositoryImpl)라 병목 후보다.
const CONTENT_SORTS = ['watcherCount', 'createdAt', 'rate'];

// 태그별 SLO(초안, 1차 측정 후 조정). 단건은 타이트하게, keyword LIKE 검색은 느슨하게.
export const options = optionsWith({
  'http_req_duration{name:contents-list}': ['p(95)<500'],
  'http_req_duration{name:contents-detail}': ['p(95)<300'],
  'http_req_duration{name:contents-search}': ['p(95)<800'],
  'http_req_duration{name:contents-sport}': ['p(95)<500'],
});

// SPORT 데이터 없이 돌리고 싶으면 -e SKIP_SPORT=1.
const SKIP_SPORT = __ENV.SKIP_SPORT === '1';

// setup: 계정이 VU 를 덮는지, 조회 대상 데이터가 있는지 시작 전에 검증한다.
export function setup() {
  assertAccountsCoverVus();
  const { accessToken } = login(seedEmail(ACCOUNT_OFFSET), SEED_PASSWORD);

  // 콘텐츠 자체가 없으면 목록/상세/검색 전부 빈 결과라 부하 의미가 없다.
  const listRes = http.get(`${BASE_URL}/api/contents?limit=1`, authParams(accessToken));
  let hasContent = false;
  try {
    hasContent = (listRes.json('data') || []).length > 0;
  } catch (_) {
    hasContent = false;
  }
  if (!hasContent) {
    throw new Error('콘텐츠가 없습니다. seed/bulk-seed.sql 시딩 또는 TMDB 수집으로 DB 를 먼저 채우세요.');
  }

  // SPORT 필터 시나리오는 SPORT 데이터가 있어야 병목이 드러난다.
  let hasSport = false;
  if (!SKIP_SPORT) {
    const sportRes = http.get(`${BASE_URL}/api/contents?typeEqual=SPORT&limit=1`, authParams(accessToken));
    try {
      hasSport = (sportRes.json('data') || []).length > 0;
    } catch (_) {
      hasSport = false;
    }
    if (!hasSport) {
      throw new Error(
        'SPORT 콘텐츠가 없습니다. seed/bulk-seed.sql 을 -v sport_content_count=100 과 함께 실행하거나 ' +
          '-e SKIP_SPORT=1 로 SPORT 시나리오를 건너뛰세요.'
      );
    }
  }
  return { hasSport };
}

export default function (data) {
  const { accessToken } = getSession();
  const sortBy = CONTENT_SORTS[__ITER % CONTENT_SORTS.length];

  // 1) 목록 커서 순회 (정렬 회전, MAX_PAGES 만큼)
  const items = fetchCursorPages(
    `${BASE_URL}/api/contents?sortBy=${sortBy}&sortDirection=DESCENDING&limit=20`,
    accessToken,
    'contents-list'
  );

  // 2) 목록에서 얻은 콘텐츠 중 랜덤 상세 조회(항상 첫 항목만 보면 캐시 히트로 낙관 측정이 된다)
  if (items.length > 0) {
    const id = items[Math.floor(Math.random() * items.length)].id;
    const detailRes = http.get(
      `${BASE_URL}/api/contents/${id}`,
      authParams(accessToken, { tags: { name: 'contents-detail' } })
    );
    check(detailRes, { 'contents-detail: status 200': (r) => r.status === 200 });
  }

  // 3) 키워드 검색: keywordLike 는 선행 와일드카드 LIKE(순차 스캔)라 대표적 병목 후보(첫 페이지만).
  //    시딩 title('loadtest content N' / 'loadtest sport N')에 매칭된다.
  const searchRes = http.get(
    `${BASE_URL}/api/contents?keywordLike=loadtest&sortBy=createdAt&sortDirection=DESCENDING&limit=20`,
    authParams(accessToken, { tags: { name: 'contents-search' } })
  );
  checkCursorResponse(searchRes, 'contents-search');

  // 4) SPORT 타입 필터: SPORT 수집은 구현돼 있으나 기본 off 라 데이터는 시딩으로 채운다(seed/bulk-seed.sql).
  if (data.hasSport) {
    const sportRes = http.get(
      `${BASE_URL}/api/contents?typeEqual=SPORT&sortBy=watcherCount&sortDirection=DESCENDING&limit=20`,
      authParams(accessToken, { tags: { name: 'contents-sport' } })
    );
    checkCursorResponse(sportRes, 'contents-sport');
  }

  sleep(1);
}
