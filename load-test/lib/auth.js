// 인증 헬퍼: CSRF 발급 → form-login → accessToken 반환
//
// 이 프로젝트의 로그인은 컨트롤러가 아니라 Spring Security formLogin 필터가 처리한다.
// 주의점 3가지:
//  1) CSRF: 로그인 POST 도 CSRF 대상. GET /api/auth/csrf-token 으로 XSRF-TOKEN 쿠키를 받고
//     그 값을 X-XSRF-TOKEN 헤더로 되돌려줘야 한다.
//  2) 파라미터명: usernameParameter 커스텀이 없어 기본값 `username`/`password` 를 쓴다.
//     `username` 에 "이메일"을 담는다(DTO의 email 필드는 Swagger 노출용일 뿐 실제로 안 쓰임).
//  3) form-urlencoded: JSON 이 아니라 application/x-www-form-urlencoded 로 보낸다.
import http from 'k6/http';
import { check, fail } from 'k6';
import { BASE_URL } from './http.js';

const CSRF_COOKIE = 'XSRF-TOKEN';
const CSRF_HEADER = 'X-XSRF-TOKEN';

// 응답 Set-Cookie(및 VU 쿠키 jar)에서 CSRF 토큰 값을 꺼낸다.
function readCsrfToken(res) {
  const jar = http.cookieJar();
  const cookies = jar.cookiesForURL(BASE_URL);
  if (cookies[CSRF_COOKIE] && cookies[CSRF_COOKIE].length > 0) {
    return cookies[CSRF_COOKIE][0];
  }
  // fallback: 응답 쿠키에서 직접
  if (res && res.cookies && res.cookies[CSRF_COOKIE]) {
    return res.cookies[CSRF_COOKIE][0].value;
  }
  return null;
}

// CSRF 토큰 발급(204 + Set-Cookie: XSRF-TOKEN). 쿠키는 VU jar 에 자동 저장된다.
export function fetchCsrfToken() {
  const res = http.get(`${BASE_URL}/api/auth/csrf-token`, { tags: { name: 'csrf-token' } });
  check(res, { 'csrf-token: status 204': (r) => r.status === 204 });
  return readCsrfToken(res);
}

// 로그인 → { accessToken, userId }. 실패 시 fail.
// 부하 중 매 iteration 이 아니라 setup/VU 초기화에서 1회 호출하는 것을 권장(access 토큰 10분).
export function login(email, password) {
  const csrf = fetchCsrfToken();
  if (!csrf) {
    fail('CSRF 토큰을 얻지 못했습니다. csrf-token 응답 확인 필요.');
  }

  const res = http.post(
    `${BASE_URL}/api/auth/sign-in`,
    // 기본 파라미터명 username 에 이메일을 담는다.
    { username: email, password: password },
    {
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
        [CSRF_HEADER]: csrf,
      },
      tags: { name: 'sign-in' },
    }
  );

  const ok = check(res, {
    'sign-in: status 200': (r) => r.status === 200,
    'sign-in: has accessToken': (r) => {
      try {
        return typeof r.json('accessToken') === 'string';
      } catch (_) {
        return false;
      }
    },
  });
  if (!ok) {
    fail(`로그인 실패 (status ${res.status}): ${String(res.body).slice(0, 200)}`);
  }

  return {
    accessToken: res.json('accessToken'),
    userId: res.json('userDto.id'),
  };
}

// access 토큰 만료(10분) 대응. 부하가 10분 넘어가면 사용.
// refresh 토큰은 REFRESH_TOKEN 쿠키에 있고 VU jar 에 저장돼 있다.
// export function refresh() {
//   const res = http.post(`${BASE_URL}/api/auth/refresh`, null, { tags: { name: 'refresh' } });
//   check(res, { 'refresh: status 200': (r) => r.status === 200 });
//   return res.json('accessToken');
// }
