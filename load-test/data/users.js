// 부하용 테스트 계정 로더. SharedArray 로 VU 간 메모리를 공유한다(init 컨텍스트에서 1회 로드).
import { SharedArray } from 'k6/data';

export const users = new SharedArray('users', () => JSON.parse(open('./users.json')));

// 인덱스로 계정 선택(범위 밖이면 랜덤). 시나리오에서 pickUser(0) 등으로 사용.
export function pickUser(index) {
  if (users.length === 0) {
    throw new Error('users.json 이 비어 있습니다. seed-users.js 로 계정을 먼저 생성하세요.');
  }
  if (typeof index === 'number' && index >= 0 && index < users.length) {
    return users[index];
  }
  return users[Math.floor(Math.random() * users.length)];
}
