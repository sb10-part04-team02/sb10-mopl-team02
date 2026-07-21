// 대용량 더미 유저(load-test/dummy/10_users.sql)의 로그인 계정 목록을 생성한다.
// 이 계정들은 이미 DB에 있으므로(더미 적재 시 INSERT) API 로 만들 필요가 없다 — 목록화만 한다.
// 10_users.sql 규칙: 이메일 = 'dummy_bulk_' + lpad(n,7,'0') + '@mopl.test', 공통 비번 'password1!'.
//
// 실행: node load-test/gen-dummy-users.mjs [count]   (기본 10000)
//   → load-test/data/dummy-users.json (u = 1..count)
// count 는 적재 SCALE 이하의 유저 수여야 한다(SCALE=1.0 → 최대 100000, SCALE=0.01 → 최대 1000).
import { writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const count = Number(process.argv[2] || 10000);
if (!Number.isInteger(count) || count < 1) {
  console.error(`count 는 1 이상의 정수여야 합니다. 받은 값: ${process.argv[2]}`);
  process.exit(1);
}

const users = Array.from({ length: count }, (_, i) => {
  const n = i + 1; // n=0 은 관리자라 제외하고 1 부터
  return {
    email: `dummy_bulk_${String(n).padStart(7, '0')}@mopl.test`,
    password: 'password1!',
  };
});

const outPath = join(dirname(fileURLToPath(import.meta.url)), 'data', 'dummy-users.json');
writeFileSync(outPath, JSON.stringify(users, null, 0));
console.log(`${users.length}개 더미 계정 → ${outPath}`);
console.log(`  첫 계정: ${users[0].email}`);
console.log(`  끝 계정: ${users[users.length - 1].email}`);
