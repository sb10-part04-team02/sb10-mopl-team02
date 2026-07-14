// SSE 스트리밍 부하: 알림/DM 실시간 채널(GET /api/sse) 연결을 다수 유지하며 서버 리소스를 관찰한다.
//
// 이 시나리오만의 특이점
//   1) k6 코어 http 로는 SSE 를 다룰 수 없다. 스트림이 안 닫혀 emitter 타임아웃(서버 30분)까지 블록되고
//      그 요청이 http_req_failed 를 오염시킨다. 그래서 xk6-sse 확장으로 빌드한 바이너리가 필요하다:
//        xk6 build --with github.com/phymbert/xk6-sse
//      (docker: grafana/xk6 이미지로 빌드 후 그 k6 로 실행)
//   2) 부하 모델이 RPS(arrival-rate)가 아니라 "동시 연결 유지"다. 그래서 config/index.js 의 프로파일을
//      재사용하지 않고 아래에서 constant-vus 로 직접 정의한다(SSE_CLIENTS 가 동시 연결 수).
//   3) SLO 도 조회용 http_req_duration 이 부적합하다(연결이 길게 열려 있는 게 정상). checks 통과율만 본다.
//
// 관찰 포인트: 연결 수를 SSE_CLIENTS 로 올려가며 서버의 emitter 수/스레드/힙을 Grafana 로 본다.
// 실행: k6 run -e BASE_URL=... -e SSE_CLIENTS=50 -e SSE_HOLD=30 load-test/scenarios/sse-read.js
import sse from 'k6/x/sse';
import { check } from 'k6';
import { BASE_URL } from '../lib/http.js';
import { getSession, assertAccountsCoverVus } from '../lib/accounts.js';

// 동시에 유지할 SSE 연결 수(= VU 수).
const SSE_CLIENTS = Number(__ENV.SSE_CLIENTS || 50);
// 연결을 유지할 시간(초). 이 시간이 지나면 클라이언트가 닫는다.
const SSE_HOLD = Number(__ENV.SSE_HOLD || 30);
// 부하 지속 시간(연결이 계속 새로 열리고 닫히도록).
const SSE_DURATION = __ENV.SSE_DURATION || '2m';

export const options = {
  scenarios: {
    sse: {
      executor: 'constant-vus',
      vus: SSE_CLIENTS,
      duration: SSE_DURATION,
    },
  },
  thresholds: {
    checks: ['rate>0.99'],
  },
};

export function setup() {
  assertAccountsCoverVus();
  return {};
}

export default function () {
  const { accessToken } = getSession();

  const response = sse.open(
    `${BASE_URL}/api/sse`,
    { headers: { Authorization: `Bearer ${accessToken}` }, tags: { name: 'sse-connect' } },
    function (client) {
      let events = 0;
      // 서버는 연결 직후 name='connect' 이벤트를 보낸다(SseEmitterService.sendConnectEvent).
      client.on('event', function (event) {
        events++;
        if (event.name === 'connect') {
          check(event, { 'sse: connect 이벤트 수신': () => true });
        }
      });
      client.on('error', function (e) {
        check(null, { 'sse: 에러 없음': () => false });
        client.close();
      });
      // SSE_HOLD 초 유지한 뒤 스스로 닫는다(서버 emitter 타임아웃 30분을 기다리지 않도록).
      client.setTimeout(function () {
        check(events, { 'sse: 유지 중 이벤트 1건 이상 수신': (n) => n >= 1 });
        client.close();
      }, SSE_HOLD * 1000);
    }
  );

  check(response, { 'sse: 연결 status 200': (r) => r && r.status === 200 });
}
