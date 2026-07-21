// 쓰기 시나리오용 프로파일 선택. -e CONFIG=smoke|load|stress (기본 smoke).
//
// 조회용 config/index.js 와 executor(부하 모양)는 똑같이 쓰고 threshold 만 쓰기용으로 바꾼다.
// 쓰기 시나리오는 `export { options } from '../config/write-index.js';` 로 가져다 쓴다.
import { options as smoke } from './smoke.js';
import { options as load } from './load.js';
import { options as stress } from './stress.js';
import { writeThresholds } from './write-thresholds.js';

const CONFIGS = { smoke, load, stress };
const selected = __ENV.CONFIG || 'smoke';

if (__ENV.CONFIG && !CONFIGS[selected]) {
  console.warn(
    `알 수 없는 CONFIG="${__ENV.CONFIG}". smoke|load|stress 중 하나를 쓰세요. smoke 로 fallback 합니다.`
  );
}

const base = CONFIGS[selected] || smoke;

export const options = {
  scenarios: base.scenarios,
  thresholds: writeThresholds,
};
