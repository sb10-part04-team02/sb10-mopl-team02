// VU 별 계정 분산 + 세션 캐시. 조회 시나리오가 공유하는 로그인 전략을 캡슐화한다.
//
// 왜 pickUser(0) 공유가 아니라 VU 별 분산인가
//   조회 응답에는 요청자 기준(requester-relative) 필드가 있다. 플레이리스트의 subscribedByMe,
//   알림 목록(수신자=principal), 내 구독 목록(subscriberIdEqual)은 계정마다 값이 다르다.
//   한 계정만 반복 조회하면 같은 캐시 라인과 같은 인덱스 경로만 때려 병목이 희석된다.
//   그래서 VU 마다 다른 시딩 계정(bulk{i}@mopl.test)으로 로그인해 부하를 흩뿌린다.
//
// 왜 setup 대량 로그인이 아니라 VU 별 lazy 로그인인가 (트레이드오프)
//   1) BCrypt(strength 10) 로그인은 ~100ms+ 라, setup 에서 200계정을 직렬 로그인하면 setup 만 수십 초다.
//   2) access 토큰 수명은 10분인데 setup 이 길면 그 시간이 토큰 수명에서 깎여 나간다.
//   3) refresh 토큰 쿠키는 login 한 컨텍스트의 VU jar 에 저장된다. setup 에서 받으면 그 쿠키가
//      setup 에 갇혀 부하 중 refresh 가 불가능하다(lib/auth.js refresh 주석 참고).
//   lazy 로그인은 로그인 비용이 arrival-rate 램프업 구간에 자연 분산되고, sign-in 은 이미
//   태그 분리 threshold(p95<1500)가 있어 조회 SLO 를 오염시키지 않는다(VU 당 1회라 표본 비중도 작다).
//
// max-account-count=1 정합
//   서버는 계정당 refresh 토큰을 1개만 유지한다(app.jwt.redis.max-account-count=1).
//   VU 당 고유 계정을 쓰므로 단일 실행 안에서 같은 계정 동시 로그인이 없어 문제가 없다.
//   여러 조회 시나리오를 동시에 돌릴 때만 -e ACCOUNT_OFFSET 으로 계정 대역을 분리한다.
import exec from 'k6/execution';
import { fail } from 'k6';
import { login, fetchCsrfToken, refresh } from './auth.js';

// 로그인에 쓸 시딩 계정 수. seed/bulk-seed.sql 의 user_count 이하여야 한다.
export const ACCOUNTS = Number(__ENV.ACCOUNTS || 200);
// 계정 대역 시작 오프셋. 여러 시나리오 병렬 실행 시 겹침을 피하려면 대역을 나눈다.
// (ACCOUNT_OFFSET + ACCOUNTS 는 seed 의 user_count 이하여야 한다.)
export const ACCOUNT_OFFSET = Number(__ENV.ACCOUNT_OFFSET || 0);
// seed/bulk-seed.sql 이 심은 공통 평문 비밀번호.
export const SEED_PASSWORD = __ENV.SEED_PASSWORD || 'loadtest1234';
// 발급 후 이 시간이 지나면 refresh 로 access 토큰을 갱신한다(만료 10분 - 여유 2분).
const REFRESH_AFTER_MS = Number(__ENV.REFRESH_AFTER_MS || 8 * 60 * 1000);

export function seedEmail(index) {
  return `bulk${index}@mopl.test`;
}

// 이 실행이 띄울 수 있는 최대 VU 수. arrival-rate 계열은 maxVUs, constant-vus 는 vus.
export function plannedMaxVus() {
  const scenarios = exec.test.options.scenarios || {};
  let max = 0;
  for (const name in scenarios) {
    const s = scenarios[name];
    max = Math.max(max, Number(s.maxVUs || s.vus || 0));
  }
  return max;
}

// setup 에서 호출. maxVUs 가 ACCOUNTS 보다 크면 VU 인덱스가 한 바퀴 돌아 계정이 겹친다.
// 겹치면 요청자 기준 필드가 의도와 달리 섞이고, 같은 계정 동시 로그인으로 refresh 토큰이 밀려난다.
// 부하를 다 돌린 뒤 알아차리면 늦으므로 시작 전에 중단한다.
export function assertAccountsCoverVus() {
  const maxVus = plannedMaxVus();
  if (maxVus > ACCOUNTS) {
    fail(
      `ACCOUNTS(${ACCOUNTS}) 가 maxVUs(${maxVus}) 보다 작습니다. ` +
        `VU 인덱스가 겹쳐 요청자 기준 조회가 섞이고 같은 계정 동시 로그인이 발생합니다. ` +
        `-e ACCOUNTS=${maxVus} 이상으로 주고 seed 의 user_count 도 그만큼 확보하세요.`
    );
  }
}

// VU 별 세션. 최초 1회 로그인해 캐시하고, 발급 후 REFRESH_AFTER_MS 가 지나면 refresh 로 갱신한다.
// login 은 iteration 마다가 아니라 VU 당 1회만 돈다(BCrypt 비용이 조회 측정을 오염시키므로).
let session = null;

export function getSession() {
  if (session === null) {
    // idInTest 는 1부터 시작하므로 계정 인덱스로 쓰려면 -1 한다.
    const accountIndex = ACCOUNT_OFFSET + ((exec.vu.idInTest - 1) % ACCOUNTS);
    const { accessToken, userId } = login(seedEmail(accountIndex), SEED_PASSWORD);
    // CSRF 토큰은 VU 쿠키 jar 기준으로 유효하므로 1회 받아 refresh 에 재사용한다.
    const csrf = fetchCsrfToken();
    if (!csrf) {
      fail('CSRF 토큰을 얻지 못했습니다.');
    }
    session = { accessToken, userId, csrf, issuedAt: Date.now() };
  } else if (Date.now() - session.issuedAt > REFRESH_AFTER_MS) {
    // REFRESH_TOKEN 쿠키는 이 VU 의 jar 에 있으므로 refresh 가 동작한다.
    session.accessToken = refresh(session.csrf);
    session.issuedAt = Date.now();
  }
  return session;
}
