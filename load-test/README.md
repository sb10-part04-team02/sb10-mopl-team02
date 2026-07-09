# load-test — k6 부하테스트

조회는 이슈 #322, 쓰기(mutation)는 이슈 #337. 핵심 API에 대한 k6 부하테스트 환경.

## 구조

```text
load-test/
  config/
    thresholds.js        # 조회 SLO threshold (p95<500, 에러율<1%)
    write-thresholds.js  # 쓰기 threshold (조회보다 느슨, p95 는 실측 전 자리표시자)
    smoke.js             # constant-vus, 1분 sanity
    load.js              # ramping-arrival-rate, 목표 RPS 유지
    stress.js            # ramping-arrival-rate, RPS 점증 → 한계점 탐색
    index.js             # 조회용: -e CONFIG=smoke|load|stress 로 프로파일 선택
    write-index.js       # 쓰기용: 같은 executor + 쓰기 threshold
  lib/
    http.js         # BASE_URL·인증 헤더·CursorResponse check 헬퍼
    auth.js         # CSRF 발급 → form-login(username=email) → accessToken
  data/
    users.json      # 부하용 계정 목록
    users.js        # SharedArray 로더
  scenarios/
    content-browse.js       # 콘텐츠 목록·상세 조회
    review-read.js          # 리뷰 목록 조회(contentId 기준)
    playlist-read.js        # 플레이리스트 목록·상세 조회
    auth-password-reset.js  # 비밀번호 초기화 (미구현 — 전체 주석)
    review-write.js         # 리뷰 create/delete 사이클 → 평점 재집계 UPDATE 경합
    subscribe-notify.js     # 구독 플리 콘텐츠 추가 → 알림 팬아웃
  seed-users.js       # 테스트 계정 생성 스크립트(소량, API 경유)
  seed/bulk-seed.sql  # 대량 시딩(계정·콘텐츠·리뷰). 로컬/부하 전용 DB 에서만 실행
```

## 사전 준비

1. k6 설치: `brew install k6` (또는 `docker run grafana/k6`)
2. 대상 앱 기동 + 콘텐츠 데이터 시딩
   - 콘텐츠는 TMDB 수집(#311)으로 채운다. 수집은 Spring Batch가 아니라 `ApplicationRunner` 방식이라,
     `app.tmdb.run-on-startup=true` 로 앱을 기동하면 1회 수집이 돈다.
   - 빈 DB 로 조회 부하를 걸면 병목이 안 드러난다.
3. 테스트 계정 시딩:

   ```bash
   k6 run -e BASE_URL=http://localhost:8080 --iterations 1 --vus 1 load-test/seed-users.js
   ```

## 실행

```bash
# smoke (기본)
k6 run -e BASE_URL=http://localhost:8080 -e CONFIG=smoke \
  load-test/scenarios/content-browse.js

# load — 목표 RPS 조정: -e TARGET_RPS=80
k6 run -e BASE_URL=http://localhost:8080 -e CONFIG=load -e TARGET_RPS=50 \
  load-test/scenarios/content-browse.js

# stress — 피크 RPS 조정: -e PEAK_RPS=400
k6 run -e BASE_URL=http://localhost:8080 -e CONFIG=stress -e PEAK_RPS=300 \
  load-test/scenarios/content-browse.js
```

시나리오 파일만 바꾸면 리뷰(`review-read.js`)·플레이리스트(`playlist-read.js`)에 동일 config 를 적용할 수 있다.

## 쓰기 부하테스트 (#337)

조회와 달리 **대량 시딩과 환경 격리가 필수**다. 쓰기 경합·인덱스 병목은 데이터가 쌓여야 드러난다.

### 1. 대량 시딩

> 경고: `seed/bulk-seed.sql` 은 로그인 가능한 계정을 수천 개 만든다(비밀번호 평문이 공개 상수).
> 로컬 또는 부하 전용 DB 에서만 실행한다. 운영/공유 DB 절대 금지.

```bash
docker compose --env-file .env exec -T db psql -U "$DB_USERNAME" -d "$DB_NAME" \
  -v user_count=2000 -v content_count=500 -v reviews_per_content=50 \
  < load-test/seed/bulk-seed.sql
```

계정 2000 + 콘텐츠 500 + 리뷰 25,000 건이 약 1초. 재실행해도 중복이 쌓이지 않는다(idempotent).
`reviews_per_content` 는 `user_count` 를 넘을 수 없다(넘기면 유니크 제약 이유를 밝히고 중단).

스키마는 `db` 서비스 최초 기동 시 `01_schema_v8.sql` 이 initdb 로 자동 적용된다(별도 적용 불필요).
정리는 `docker compose --env-file .env down -v`(볼륨 삭제)가 가장 확실하다.

실행 절차 전체는 [로컬 부하테스트 실행 방법](../docs/local-load-test.md) 참고.

### 2. 평점 재집계 경합 (review-write.js)

리뷰 create → delete 사이클. 리뷰는 soft delete 라 삭제 직후 같은 (계정, 콘텐츠) 조합으로 다시 만들 수 있어 무한 반복이 된다.

```bash
# 경합 최대: 전 VU 가 콘텐츠 1개에 리뷰를 몰아 넣는다 (재집계 UPDATE 가 한 row 에 집중)
k6 run -e BASE_URL=http://localhost:8080 -e CONFIG=load -e FOCUS_CONTENTS=1 \
  load-test/scenarios/review-write.js

# 기준선: 콘텐츠 100개로 분산 (경합 없는 처리량)
k6 run -e BASE_URL=http://localhost:8080 -e CONFIG=load -e FOCUS_CONTENTS=100 \
  load-test/scenarios/review-write.js
```

두 실행의 `review-create` p95 차이가 곧 row-level lock 경합 비용이다.

`FOCUS_CONTENTS` 상한은 100이다. 서버의 `ContentSearchRequest.normalizedLimit()` 이 `MAX_LIMIT=100` 으로 clamp 하므로 그 이상을 줘도 100건만 온다(넘기면 스크립트가 경고한다). 분산 기준선은 100개로 충분하다.

VU 마다 다른 시딩 계정(`bulk{i}@mopl.test`)으로 로그인한다. 한 계정을 공유하면 리뷰 1개 제약 때문에 동시 생성이 409 로 튕겨 경합이 관찰되지 않는다.

`ACCOUNTS` 는 executor 의 maxVUs 이상이어야 한다(기본 200 = `load` 의 maxVUs). 작으면 VU 인덱스가 한 바퀴 돌아 계정이 겹치고, 409 가 나면 그 iteration 이 delete 를 건너뛰어 `review-create` p95 는 낮게, `review-delete` 호출 수는 적게 나온다. setup 이 이 조건을 검사해 시작 전에 중단한다.

```bash
# stress 는 maxVUs=500 → 계정도 500개 필요 (seed 의 user_count 도 500 이상)
k6 run -e CONFIG=stress -e ACCOUNTS=500 ... load-test/scenarios/review-write.js
```

> 1차 측정 전에는 쓰기 + `stress` 조합을 쓰지 않는다. `stress` 는 threshold 초과 시점을 한계점으로 판정하는데, 쓰기 threshold 의 p95 는 아직 자리표시자라 한계점이 임의 숫자가 된다. `smoke`/`load` 로 실측해 `config/write-thresholds.js` 를 채운 뒤에 쓴다.

### 3. 알림 팬아웃 (subscribe-notify.js)

```bash
k6 run -e BASE_URL=http://localhost:8080 -e SUBSCRIBERS=100 \
  load-test/scenarios/subscribe-notify.js
```

`SUBSCRIBERS` 를 0 → 10 → 100 → 500 으로 올려가며 `playlist-add-content` 의 p95 가 어떻게 자라는지 본다.
`playlist-remove-content` 는 팬아웃이 없어 대조군이 된다. 두 태그의 p95 차이가 팬아웃 비용이다.
계정 0번을 소유자로 쓰므로 시딩된 `user_count` 는 `SUBSCRIBERS + 1` 이상이어야 한다.

팬아웃은 **동기**다. `PlaylistContentAddedEventListener` 는 `@TransactionalEventListener(AFTER_COMMIT)` 만 붙어 있고 `@Async` 가 없으며 코드베이스에 `@EnableAsync` 자체가 없다. 구독자 1명당 SELECT + INSERT + SSE 전송이 요청 스레드에서 돌아 응답시간이 구독자 수에 선형 비례한다. 리스너에 `@Transactional(propagation = REQUIRES_NEW)` 도 붙어 있어, 커밋 후 커넥션을 하나 더 잡고 구독자 N명분 INSERT 를 한 트랜잭션에서 돈다.

이 시나리오가 재는 건 **팬아웃이 응답시간에 기여하는 몫**이다. HikariCP 커넥션 풀 고갈은 재지 않는다 — `vus: 1` 직렬이라 동시 add 가 없기 때문이다. 실제 운영에서는 풀 고갈이 응답시간 증가보다 먼저 터질 수 있고, 그걸 보려면 플레이리스트를 여러 개 만들어 VU 를 늘리는 별도 시나리오가 필요하다.

> `SUBSCRIBERS` 가 크면 setup 이 오래 걸린다. 구독자 1명당 로그인(BCrypt)이 붙어 500명이면 수십 초~수 분이다. owner 토큰은 setup 에서 1회 발급해 재사용하는데 수명이 10분(`access-token-expiration`)이므로, setup 시간 + `DURATION` 이 10분에 가까우면 부하 도중 401 이 난다. setup 이 잔여 수명을 계산해 경고하지만, `lib/auth.js` 의 `refresh()` 는 비활성이라 자동 대응은 없다.

### 4. 회차 간 비교를 하려면 DB 를 초기화한다

부하가 남기는 데이터는 자동으로 정리되지 않는다.

| 테이블 | 동작 | 영향 |
|---|---|---|
| `reviews` | create/delete 사이클이지만 soft delete 라 행이 남음 | 디스크만 증가. 재집계 성능에는 영향 없음 |
| `notifications` | `add` 마다 구독자 N명분 순증, `remove` 는 아무 이벤트도 발행 안 함 | 계속 쌓임 |
| `playlist_contents` | `remove` 가 하드 delete → 순환 | 누적 없음 |

`reviews` 누적이 재집계를 느리게 만들지는 않는다. `refreshRatingAggregate` 의 AVG/COUNT 는 `deleted_at IS NULL` 만 세고, `ix_reviews_content_*` 가 `WHERE deleted_at IS NULL` 부분 인덱스라 죽은 행은 인덱스에서 빠진다. 실측(PG16)으로 살아있는 리뷰 50건 + 죽은 리뷰 20만 건일 때 Index Only Scan 이 50행만 읽고 0.076ms 였다. 비용을 정하는 건 살아있는 행 수다.

반면 `notifications` 는 진짜로 순증한다. `SUBSCRIBERS=500` 으로 1분만 돌려도 수만~수십만 행이다.

그러므로 **회차 간 수치를 비교하려면 회차마다 DB 를 초기화한다.**

```bash
docker compose --env-file .env down -v && docker compose --env-file .env up -d
# 스키마는 initdb 로 자동 적용된다. 이후 bulk-seed.sql 만 재실행.
```

### 중복 제약 4xx 는 실패가 아니다

k6 는 4xx 를 기본적으로 `http_req_failed` 로 센다. 중복 제약이 정상 동작한 응답은 서버 장애가 아니므로 `responseCallback: http.expectedStatuses(...)` 로 성공 처리한다.

| 제약 | 상태 |
|---|---|
| 리뷰 1개(유저×콘텐츠) | 409 |
| 중복 구독 | 400 |
| 중복 팔로우 | 400 |

## 이 프로젝트의 인증 함정 (lib/auth.js 가 캡슐화)

- 로그인은 컨트롤러가 아니라 Spring Security formLogin 필터가 처리한다.
- 파라미터명은 DTO 의 `email` 이 아니라 기본값 **`username`** (여기에 이메일을 담는다), `application/x-www-form-urlencoded`.
- 로그인·회원가입 POST 도 **CSRF 대상** → `GET /api/auth/csrf-token` 으로 받은 `XSRF-TOKEN` 쿠키 값을 `X-XSRF-TOKEN` 헤더로 되돌린다.
- access 토큰은 10분 만료. 부하가 10분을 넘으면 `lib/auth.js` 의 `refresh()`(주석) 를 VU 루프에 넣는다. 현재는 setup 에서 1회 로그인 후 전체 VU 가 공유.

## 미구현으로 주석 처리된 부분 (origin/dev 기준)

| 항목 | 위치 | 해제 조건 |
|---|---|---|
| SPORT 타입 필터 조회 | `scenarios/content-browse.js` 하단 주석 | sport(The Sports DB) 수집 구현 → SPORT 데이터 시딩 |
| 비밀번호 초기화 인증 | `scenarios/auth-password-reset.js` 전체 주석 | 비밀번호 초기화 API 구현 후 실제 계약에 맞춰 채움 |
| access 토큰 refresh | `lib/auth.js` 의 `refresh()` 주석 | 10분 초과 장시간 실행 시 |

## 대상 환경

- 로컬 단일 인스턴스 수치는 신뢰하지 않는다(부하 발생기와 대상이 같은 머신).
- 로컬 분산(#323, `docker-compose.distributed.yml` + nginx)이면 `-e BASE_URL=<nginx 프록시 포트>`.
- 원격 CD 파이프라인은 구현 예정 → 완성 시 운영 동일 스펙 환경에서 신뢰 수치 측정.
- 부하 중 서버 지표(CPU/메모리/DB 커넥션풀/GC)는 actuator 로 병행 관찰.
