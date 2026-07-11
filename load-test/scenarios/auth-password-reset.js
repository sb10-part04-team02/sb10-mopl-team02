// 인증 시나리오 — 비밀번호 초기화 플로우 (미구현: 전체 주석 처리)
//
// 현황(origin/dev): ResetPasswordRequest·ChangePasswordRequest DTO 만 존재하고
// 컨트롤러 엔드포인트·서비스가 없다. 스펙상 임시 비밀번호는 3분 만료 후 일반 sign-in 을 재사용한다.
// API 가 구현되면 아래 골격의 경로/파라미터를 실제 계약에 맞춰 채우고 주석을 해제한다.
//
// import http from 'k6/http';
// import { check, sleep } from 'k6';
// import { BASE_URL } from '../lib/http.js';
// import { fetchCsrfToken } from '../lib/auth.js';
// import { pickUser } from '../data/users.js';
//
// export { options } from '../config/index.js';
//
// export default function () {
//   const user = pickUser(0);
//
//   // 1) 비밀번호 초기화 요청 → 임시 비밀번호 발급(3분 만료). (CSRF 필요)
//   const csrf = fetchCsrfToken();
//   const resetRes = http.post(
//     `${BASE_URL}/api/auth/reset-password`, // TODO: 실제 엔드포인트로 교체
//     JSON.stringify({ email: user.email }),
//     {
//       headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': csrf },
//       tags: { name: 'reset-password' },
//     }
//   );
//   check(resetRes, { 'reset-password: 2xx': (r) => r.status >= 200 && r.status < 300 });
//
//   // 2) 임시 비밀번호로 일반 sign-in 재사용 (lib/auth.js 의 login 사용).
//   //    임시 비밀번호는 응답/메일 등 실제 전달 경로에서 얻어야 하므로 부하 시나리오화가 까다롭다.
//
//   sleep(1);
// }
