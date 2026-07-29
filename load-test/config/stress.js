// Stress: RPS 를 계속 올려 한계점(breaking point)이 어디인지 찾는다.
// threshold 를 넘겨 exit 1 이 나는 지점 = 시스템이 SLO 를 못 지키기 시작하는 부하.
import { thresholds } from './thresholds.js';

const PEAK_RPS = Number(__ENV.PEAK_RPS || 300);

export const options = {
  scenarios: {
    stress: {
      executor: 'ramping-arrival-rate',
      startRate: 0,
      timeUnit: '1s',
      preAllocatedVUs: 100,
      maxVUs: 500,
      stages: [
        { target: Math.round(PEAK_RPS * 0.33), duration: '2m' },
        { target: Math.round(PEAK_RPS * 0.66), duration: '2m' },
        { target: PEAK_RPS, duration: '2m' }, // 피크
        { target: 0, duration: '1m' }, // 회복 관찰
      ],
    },
  },
  thresholds,
};
