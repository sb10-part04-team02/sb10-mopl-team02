// -e CONFIG=smoke|load|stress 로 실행 프로파일을 고른다(기본 smoke).
// 시나리오 파일은 `export { options } from '../config/index.js';` 로 이 옵션을 가져다 쓴다.
import { options as smoke } from './smoke.js';
import { options as load } from './load.js';
import { options as stress } from './stress.js';

const CONFIGS = { smoke, load, stress };
const selected = __ENV.CONFIG || 'smoke';

// 오타(예: -e CONFIG=stresss)로 잘못된 값이 오면 silent 로 smoke 가 돌아
// 부하 결과에 false confidence 가 생긴다. 알려진 키가 아니면 경고를 남긴다.
if (__ENV.CONFIG && !CONFIGS[selected]) {
  console.warn(`알 수 없는 CONFIG="${__ENV.CONFIG}". smoke|load|stress 중 하나를 쓰세요. smoke 로 fallback 합니다.`);
}

export const options = CONFIGS[selected] || smoke;

// 선택된 프로파일에 시나리오 자신의 태그별 threshold 를 병합해 돌려준다.
// 시나리오가 `export const options = optionsWith({ 'http_req_duration{name:contents-list}': [...] })`
// 처럼 자기 SLO 를 선언하면, 그 태그가 없는 다른 시나리오에는 노이즈가 안 생기고 모듈이 독립적이 된다.
export function optionsWith(extraThresholds) {
  const base = CONFIGS[selected] || smoke;
  return {
    ...base,
    thresholds: { ...base.thresholds, ...extraThresholds },
  };
}
