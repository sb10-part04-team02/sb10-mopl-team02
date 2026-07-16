// 공통 HTTP 유틸: BASE_URL·기본 헤더·응답 check 헬퍼
import http from 'k6/http';
import {check} from 'k6';

// 대상 환경은 -e BASE_URL=... 로 주입. 미지정 시 로컬.
// 로컬 분산(#323, nginx) 대상이면 nginx 프록시 포트를 넣는다.
export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// 목록 시나리오가 커서로 순회할 최대 페이지 수. -e MAX_PAGES=10 등으로 조정.
// 무한 순회가 아니라 상한 + hasNext 종료라, 데이터가 많아도 iteration 이 무한정 길어지지 않는다.
export const MAX_PAGES = Number(__ENV.MAX_PAGES || 3);

// accessToken 을 Bearer 헤더로 감싼 요청 파라미터를 만든다.
// extra.headers 는 headers 로 병합하고, 나머지 필드만 최상위로 편다.
// (extra 를 통째로 펴면 headers 가 덮여 Authorization 이 유실됨)
export function authParams(accessToken, extra = {}) {
  const { headers: extraHeaders, ...rest } = extra;
  return {
    headers: {
      Authorization: `Bearer ${accessToken}`,
      ...(extraHeaders || {}),
    },
    ...rest,
  };
}

// 조회 응답 공통 검증: 200 + CursorResponse 형태(data 배열 존재) + 커서 짝 계약.
// check 실패는 테스트를 중단시키지 않고 통과율만 기록한다(실패 판정은 threshold의 checks 로).
//
// 커서 짝 계약: cursor 와 idAfter 는 둘 다 있거나 둘 다 없어야 한다.
// hasNext=true 인데 nextCursor/nextIdAfter 중 하나라도 비면 다음 페이지 요청이 400 이 되므로,
// 응답 자체가 그 짝을 지키는지 검증한다.
export function checkCursorResponse(res, name) {
  return check(res, {
    [`${name}: status 200`]: (r) => r.status === 200,
    [`${name}: has data array`]: (r) => {
      try {
        return Array.isArray(r.json('data'));
      } catch (_) {
        return false;
      }
    },
    [`${name}: cursor pair contract`]: (r) => {
      try {
        const body = r.json();
        if (body.hasNext !== true) {
          return true;
        }
        return body.nextCursor != null && body.nextIdAfter != null;
      } catch (_) {
        return false;
      }
    },
  });
}

// 커서 목록을 여러 페이지 순회한다.
//   urlBase: cursor/idAfter 를 제외한 완성 URL(예: `${BASE_URL}/api/contents?sortBy=...&limit=20`)
//   name   : threshold/집계용 태그명(페이지별로 나누지 않고 같은 태그로 모은다)
// hasNext=false 또는 maxPages 도달 시 멈추고, 순회하며 모은 data 항목 배열을 반환한다.
export function fetchCursorPages(urlBase, accessToken, name, maxPages = MAX_PAGES) {
  const items = [];  // 모든 페이지의 항목을 누적할 빈 배열.
  let cursor = null;  // 다음 페이지 커서. 첫 요청은 null
  let idAfter = null; // 커서와 짝을 이루는 id 값. 첫 요청은 null
  // page를 0부터 maxPages 미만까지 1씩 증가시키며 반복. 최대 maxPages번 실행
  for (let page = 0; page < maxPages; page++) {
    const url = cursor
      ? `${urlBase}&cursor=${encodeURIComponent(cursor)}&idAfter=${encodeURIComponent(idAfter)}`
      : urlBase;
    // 요청에 태그 붙임
    const res = http.get(url, authParams(accessToken, { tags: { name } }));
    if (!checkCursorResponse(res, name)) { // 응답 검증
      break;
    }
    let body; // 파싱 결과를 담을 변수
    try {
      body = res.json(); // 응답 본문을 JSON으로 파싱
    } catch (_) {
      break;
    }
    if (Array.isArray(body.data)) {
      for (const item of body.data) {
        items.push(item);
      }
    }
    // 아래 조건 중 하나가 참이면 더 볼 페이지 없음 -> 종료
    if (body.hasNext !== true || body.nextCursor == null || body.nextIdAfter == null) {
      break;
    }
    // 다음 반복을 위해 커서/idAfter 갱신
    cursor = body.nextCursor;
    idAfter = body.nextIdAfter;
  }
  return items; // 순회하며 모은 전체 항목 배열 반환
}
