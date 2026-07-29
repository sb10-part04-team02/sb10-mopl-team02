// 대용량 더미 계정 로더(load-test/dummy 적재분 전용).
// data/users.js 와 분리한 이유: SharedArray 의 init(open) 은 이 모듈이 import 되는 순간 실행된다.
// users.js 에 두면 pickUser 만 쓰는 기존 시나리오도 dummy-users.json 을 강제로 요구하게 되므로,
// 더미 계정을 쓰는 시나리오(review-read/playlist-read/subscribe-write)만 이 모듈을 import 한다.
//
// gen-dummy-users.mjs 로 data/dummy-users.json 을 먼저 생성해야 한다.
import { SharedArray } from 'k6/data';

export const dummyUsers = new SharedArray('dummyUsers', () =>
  JSON.parse(open('./dummy-users.json'))
);

// 인덱스로 더미 계정 선택(범위 밖이면 랜덤). VU 인덱스(__VU)를 넣어 VU 간 계정을 분할한다.
export function pickDummyUser(index) {
  if (dummyUsers.length === 0) {
    throw new Error(
      'dummy-users.json 이 비어 있습니다. `node load-test/gen-dummy-users.mjs` 로 먼저 생성하세요.'
    );
  }
  if (typeof index === 'number' && index >= 0 && index < dummyUsers.length) {
    return dummyUsers[index];
  }
  return dummyUsers[Math.floor(Math.random() * dummyUsers.length)];
}
