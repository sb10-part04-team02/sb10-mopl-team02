// Load: 예상 평시/피크 트래픽을 견디는지(SLO 검증).
// RPS 기준(arrival-rate)으로 모델링 — 응답이 느려져도 부하를 일정하게 유지해 병목을 드러낸다.
// TARGET_RPS 는 -e TARGET_RPS=50 등으로 조정.
import { thresholds } from './thresholds.js';

const TARGET_RPS = Number(__ENV.TARGET_RPS || 50);

export const options = {
  scenarios: {
    load: {
      executor: 'ramping-arrival-rate',
      startRate: 0,
      timeUnit: '1s',
      preAllocatedVUs: 50,
      maxVUs: 200,
      stages: [
        { target: TARGET_RPS, duration: '1m' }, // 워밍업 상승
        { target: TARGET_RPS, duration: '3m' }, // 목표 RPS 유지
        { target: 0, duration: '30s' }, // 하강
      ],
    },
  },
  thresholds,
};
