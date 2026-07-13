// 공통 HTTP 유틸: BASE_URL·기본 헤더·응답 check 헬퍼
import { check } from 'k6';

// 대상 환경은 -e BASE_URL=... 로 주입. 미지정 시 로컬.
// 로컬 분산(#323, nginx) 대상이면 nginx 프록시 포트를 넣는다.
export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

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

// 조회 응답 공통 검증: 200 + CursorResponse 형태(data 배열 존재).
// check 실패는 테스트를 중단시키지 않고 통과율만 기록한다(실패 판정은 threshold의 checks 로).
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
  });
}
