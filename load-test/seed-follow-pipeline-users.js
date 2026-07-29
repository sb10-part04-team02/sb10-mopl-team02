// 팔로우 기반 알림 생성 파이프라인 부하테스트용 계정 시딩 스크립트.
//
// 실행:
//   k6 run -e BASE_URL=http://localhost:8080 --iterations 1 --vus 1 load-test/seed-follow-pipeline-users.js
import http from 'k6/http';
import {check} from 'k6';
import {BASE_URL} from './lib/http.js';
import {fetchCsrfToken} from './lib/auth.js';
import {followPipelineUsers} from './data/follow-pipeline-users.js';

export const options = {iterations: 1, vus: 1};

export default function () {
  for (const user of followPipelineUsers) {
    const csrf = fetchCsrfToken();

    const res = http.post(`${BASE_URL}/api/users`, JSON.stringify(user), {
      headers: {
        'Content-Type': 'application/json',
        'X-XSRF-TOKEN': csrf,
      },
      tags: {name: 'seed-follow-pipeline-user'},
    });

    check(res, {
      [`seed ${user.email}: created or exists`]: (r) => r.status === 201
          || r.status === 409,
    });

    console.log(`seed ${user.email} -> ${res.status}`);
  }
}