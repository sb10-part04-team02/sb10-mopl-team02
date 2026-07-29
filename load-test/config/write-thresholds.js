// 쓰기(mutation) 전용 threshold.
//
// 조회(config/thresholds.js)와 분리하는 이유: 쓰기는 트랜잭션·평점 재집계 UPDATE·
// 알림 팬아웃이 껴서 조회보다 근본적으로 느리다. 같은 p95 기준을 걸면 의미가 없다.
//
// 응답시간 p(95) 숫자는 전부 자리표시자다. 1차 측정 전에 값을 정하는 건 근거 없는 숫자를
// 박는 것이므로, 실측 후 이 파일을 실측 기반으로 갱신한다.
// 에러율·checks 통과율은 실측 없이도 정할 수 있는 상식값이라 그대로 둔다.
//
// 그래서 1차 측정 전에는 `-e CONFIG=stress` 를 쓰지 않는다.
// stress 는 threshold 초과 시점을 "한계점"으로 판정하는 설계인데(config/stress.js),
// 기준이 자리표시자면 한계점도 임의 숫자가 된다. smoke/load 로 먼저 실측하고 값을 채운 뒤 쓴다.
export const writeThresholds = {
  // 쓰기 에러율 2% 미만. 중복 제약 4xx 는 시나리오의 check 에서 정상으로 분류하므로
  // 여기 http_req_failed 에는 잡히지 않는다(k6 는 4xx 를 기본적으로 실패로 센다는 점에 주의 —
  // 시나리오에서 responseCallback 으로 정상 4xx 를 성공 처리한다).
  http_req_failed: ['rate<0.02'],
  checks: ['rate>0.98'],

  // 태그별 기준(자리표시자)
  'http_req_duration{name:playlist-add-content}': ['p(95)<2000'],
  'http_req_duration{name:playlist-remove-content}': ['p(95)<1000'],

  // 리뷰 쓰기: hotspot(콘텐츠 소수에 리뷰 집중, average_rating 재집계 UPDATE 락 경합)과
  // spread(넓게 분산) 를 mode 하위태그로 나눠 대조한다. delete 도 재집계를 돈다.
  'http_req_duration{name:review-create,mode:hotspot}': ['p(95)<1000'],
  'http_req_duration{name:review-create,mode:spread}': ['p(95)<1000'],
  'http_req_duration{name:review-delete}': ['p(95)<1000'],

  // 구독 쓰기: hotspot(인기 플리 1개에 동시 구독, 같은 행 락 경합)과
  // spread(여러 플리에 분산) 를 같은 태그명 아래 mode 하위태그로 나눠 대조한다.
  'http_req_duration{name:subscribe,mode:hotspot}': ['p(95)<1000'],
  'http_req_duration{name:subscribe,mode:spread}': ['p(95)<1000'],
  'http_req_duration{name:unsubscribe}': ['p(95)<1000'],

  // 로그인은 BCrypt 가 있어 별도 기준(조회 config 와 동일 이유).
  'http_req_duration{name:sign-in}': ['p(95)<1500'],
};
