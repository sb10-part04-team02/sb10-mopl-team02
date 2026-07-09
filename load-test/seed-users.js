// 부하용 테스트 계정 시딩 스크립트(부하테스트가 아님).
// data/users.json 의 계정들을 POST /api/users 로 생성한다.
// 회원가입도 CSRF 대상이라 XSRF 토큰을 붙인다. 이미 존재하는 계정은 4xx 가 나도 무시.
//
// 실행: k6 run -e BASE_URL=http://localhost:8080 --iterations 1 --vus 1 load-test/seed-users.js
import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL } from './lib/http.js';
import { fetchCsrfToken } from './lib/auth.js';

const users = JSON.parse(open('./data/users.json'));

export const options = { iterations: 1, vus: 1 };

export default function () {
  for (const u of users) {
    const csrf = fetchCsrfToken();
    const res = http.post(`${BASE_URL}/api/users`, JSON.stringify(u), {
      headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': csrf },
      tags: { name: 'seed-user' },
    });
    // 201 생성 또는 이미 존재(중복 이메일 4xx) 모두 시딩 관점에선 통과.
    check(res, {
      [`seed ${u.email}: created or exists`]: (r) =>
        r.status === 201 || (r.status >= 400 && r.status < 500),
    });
    console.log(`seed ${u.email} → ${res.status}`);
  }
}
