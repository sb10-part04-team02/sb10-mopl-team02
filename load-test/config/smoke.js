// Smoke: 최소 부하에서 스크립트·시스템이 정상 동작하는지(sanity).
// 수치를 재는 게 아니라 "돌아가는지"만 본다.
import { thresholds } from './thresholds.js';

export const options = {
  scenarios: {
    smoke: {
      executor: 'constant-vus',
      vus: 3,
      duration: '1m',
    },
  },
  thresholds,
};
