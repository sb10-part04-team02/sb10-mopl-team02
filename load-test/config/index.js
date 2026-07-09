// -e CONFIG=smoke|load|stress 로 실행 프로파일을 고른다(기본 smoke).
// 시나리오 파일은 `export { options } from '../config/index.js';` 로 이 옵션을 가져다 쓴다.
import { options as smoke } from './smoke.js';
import { options as load } from './load.js';
import { options as stress } from './stress.js';

const CONFIGS = { smoke, load, stress };
const selected = __ENV.CONFIG || 'smoke';

export const options = CONFIGS[selected] || smoke;
