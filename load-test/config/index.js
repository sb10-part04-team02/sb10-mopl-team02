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
