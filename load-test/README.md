# load-test - k6 부하테스트 (조회)

이슈 #322(최초 구성), #384(완성). 콘텐츠/리뷰/플레이리스트/소셜 조회 API에 대한 k6 부하테스트 환경.
쓰기(mutation) 부하는 이슈 #337에서 별도로 다룬다.

## 구조

```text
load-test/
  config/
    thresholds.js   # 전 시나리오 공통 SLO (p95<500, 에러율<1%). 태그별 SLO는 각 시나리오가 선언
    smoke.js        # constant-vus, 1분 sanity
    load.js         # ramping-arrival-rate, 목표 RPS 유지
    stress.js       # ramping-arrival-rate, RPS 점증으로 한계점 탐색
    index.js        # -e CONFIG=smoke|load|stress 선택 + optionsWith(태그 SLO 주입)
  lib/
    http.js         # BASE_URL, 인증 헤더, CursorResponse check, 커서 순회(fetchCursorPages)
    auth.js         # CSRF 발급, form-login(username=email), accessToken, refresh
    accounts.js     # VU별 시딩 계정 분산 + 세션 캐시(getSession) + 만료 시 refresh
  data/
    users.json      # 소량 계정 목록(seed-users.js 용)
    users.js        # SharedArray 로더
  scenarios/
    content-browse.js       # 콘텐츠: 목록 커서 순회, 상세, 키워드 검색, SPORT 필터
    review-read.js          # 리뷰: 콘텐츠별 목록 커서 순회(정렬 회전)
    playlist-read.js        # 플레이리스트: 목록, 내 구독, 검색, 상세
    notification-read.js    # 알림 목록 커서 순회
    dm-read.js              # 대화 목록 -> 메시지 목록 커서 순회
    profile-read.js         # 프로필 방문(단건, 팔로워 수, 팔로우 여부, 시청 세션)
    admin-user-read.js      # ADMIN 유저 목록(ADMIN 자격 필요)
    sse-read.js             # SSE 연결 유지(xk6-sse 필요)
    auth-password-reset.js  # 비밀번호 초기화 (백엔드 미구현, 전체 주석)
  seed-users.js         # 소량 계정 생성(API 경유)
  seed/
    bulk-seed.sql         # 대량 계정/콘텐츠(MOVIE+SPORT)/리뷰 시딩
    bulk-seed-social.sql  # 플레이리스트/구독/팔로우/알림/대화/DM 시딩
```

## 사전 준비

### 1. k6 설치

```bash
brew install k6   # 또는 docker run grafana/k6
```

SSE 시나리오(`sse-read.js`)는 xk6-sse 확장을 넣은 커스텀 바이너리가 필요하다:

```bash
xk6 build --with github.com/phymbert/xk6-sse
```

### 2. 인프라 기동

로그인이 Redis에 의존한다(JWT refresh 토큰 저장). DB만으로는 로그인 자체가 실패하므로 최소 DB + Redis가 필요하다.
기본 `docker-compose.yml`은 db + redis + prometheus + grafana를 띄운다(Kafka는 없음. 조회는 알림을 발행하지 않아 무관하나 앱 로그에 Kafka 연결 경고가 남는다).

```bash
cp .env.example .env   # 최초 1회
docker compose --env-file .env up -d
```

### 3. 앱 기동(스키마는 Flyway가 생성)

스키마는 이제 Flyway가 앱 기동 시 생성한다(#378, `src/main/resources/db/migration/`). postgres initdb 자동 적용 방식은 폐기됐다.
(참고: `docker-compose.yml`의 db 서비스가 삭제된 `01_schema_v9.sql`을 마운트하는 stale 참조가 남아 있다. 스키마는 앱 Flyway가 만들므로 동작에는 문제 없다. 정리는 #379 참고.)

```bash
./gradlew bootRun
```

### 4. 데이터 시딩

빈 DB로 조회 부하를 걸면 병목이 안 드러난다. 조회 시나리오는 `bulk{i}@mopl.test` 대량 계정으로 VU를 분산하므로 bulk 시딩이 사실상 필수다.

> 경고: `seed/bulk-seed.sql`은 로그인 가능한 계정을 수천 개 만든다(비밀번호 평문 `loadtest1234`가 저장소에 공개). 로컬 또는 부하 전용 DB에서만 실행한다. 운영/공유 DB 절대 금지.

```bash
# 계정 2000 + 콘텐츠(MOVIE 500 + SPORT 100) + 리뷰 25000
docker compose --env-file .env exec -T db psql -U "$DB_USERNAME" -d "$DB_NAME" \
  -v user_count=2000 -v content_count=500 -v reviews_per_content=50 -v sport_content_count=100 \
  < load-test/seed/bulk-seed.sql

# 소셜 그래프(플레이리스트/구독/팔로우/알림/대화/DM). bulk-seed.sql 선행 필수
docker compose --env-file .env exec -T db psql -U "$DB_USERNAME" -d "$DB_NAME" \
  < load-test/seed/bulk-seed-social.sql
```

두 스크립트 모두 멱등이라 재실행해도 중복이 쌓이지 않는다. 소량 계정만 필요하면(3개) `seed-users.js`를 쓴다.

## 실행

```bash
# smoke (기본): 스크립트/시스템 sanity
k6 run -e BASE_URL=http://localhost:8080 -e CONFIG=smoke \
  load-test/scenarios/content-browse.js

# load: 목표 RPS 유지 (-e TARGET_RPS=50)
k6 run -e BASE_URL=http://localhost:8080 -e CONFIG=load -e TARGET_RPS=50 \
  load-test/scenarios/content-browse.js

# stress: RPS 점증으로 한계점 탐색 (-e PEAK_RPS=300)
k6 run -e BASE_URL=http://localhost:8080 -e CONFIG=stress -e PEAK_RPS=300 \
  load-test/scenarios/content-browse.js
```

시나리오 파일만 바꾸면 같은 config를 다른 시나리오에 적용할 수 있다. ADMIN/SSE는 아래 별도 주의를 참고한다.

### arrival-rate와 여정(journey) 길이

`load`/`stress`의 `TARGET_RPS`/`PEAK_RPS`는 **여정(iteration) 시작률**이지 HTTP 요청률이 아니다.
한 여정이 k개의 요청을 보내면 서버 실측 RPS는 대략 `RPS * k`가 된다(예: content-browse는 목록 순회 + 상세 + 검색 + SPORT로 여정당 여러 요청). 기존 단일 요청 시나리오와 절대 수치를 비교할 때 이 배수를 감안한다.

또한 `MAX_PAGES`를 키우면 여정이 길어져 arrival-rate가 VU를 다 써 `dropped_iterations`가 생길 수 있다. **`dropped_iterations`가 0이 아니면 목표 부하에 도달하지 못한 것이므로 결과를 신뢰하지 않는다.** 이때는 `-e TARGET_RPS`를 낮추거나 config의 maxVUs를 늘린다.

## 환경 변수

| 변수 | 기본값 | 설명 |
|---|---|---|
| `BASE_URL` | `http://localhost:8080` | 대상 앱 주소(로컬 분산이면 nginx 프록시 포트) |
| `CONFIG` | `smoke` | `smoke`\|`load`\|`stress` |
| `TARGET_RPS` | 50 | `load`의 목표 여정 시작률 |
| `PEAK_RPS` | 300 | `stress`의 피크 여정 시작률 |
| `MAX_PAGES` | 3 | 목록 시나리오의 커서 순회 최대 페이지 수 |
| `ACCOUNTS` | 200 | 로그인에 쓸 bulk 계정 수(maxVUs 이상, seed user_count 이하) |
| `ACCOUNT_OFFSET` | 0 | 계정 대역 시작 오프셋(여러 시나리오 병렬 실행 시 겹침 방지) |
| `SEED_PASSWORD` | `loadtest1234` | bulk 계정 공통 비밀번호 |
| `REFRESH_AFTER_MS` | 480000 | 발급 후 이 시간 지나면 refresh(10분 만료 대비). 검증 시 작게 줘 강제 발동 |
| `SKIP_SPORT` | (없음) | `1`이면 content-browse의 SPORT 시나리오 생략 |
| `ADMIN_EMAIL`/`ADMIN_PASSWORD` | (없음) | admin-user-read 자격(미지정 시 setup 중단) |
| `SSE_CLIENTS` | 50 | sse-read 동시 연결 수 |
| `SSE_HOLD` | 30 | sse-read 연결 유지 시간(초) |
| `SSE_DURATION` | `2m` | sse-read 부하 지속 시간 |

## 계정 분산 전략

조회 응답에는 요청자 기준 필드(플레이리스트 `subscribedByMe`, 알림 수신자, 내 구독 목록)가 있어, 한 계정만 반복 조회하면 병목이 희석된다. 그래서 `lib/accounts.js`의 `getSession()`이 VU마다 다른 `bulk{i}@mopl.test` 계정으로 로그인한다.

- `ACCOUNTS`는 실행 프로파일의 maxVUs 이상이어야 한다(작으면 계정이 겹쳐 요청자 기준 조회가 섞이고, setup이 중단한다).
- 여러 조회 시나리오를 동시에 돌리면 `ACCOUNT_OFFSET`으로 계정 대역을 분리한다. 서버가 계정당 refresh 토큰을 1개만 유지하므로(`app.jwt.redis.max-account-count=1`) 같은 계정 동시 로그인은 서로를 밀어낸다.
- 로그인은 VU당 최초 1회만 돈다(BCrypt 비용이 조회 측정을 오염시키므로). sign-in은 태그 분리 threshold(p95<1500)로 따로 관리된다.

## 이 프로젝트의 인증 함정 (lib/auth.js가 캡슐화)

- 로그인은 컨트롤러가 아니라 Spring Security formLogin 필터가 처리한다.
- 파라미터명은 DTO의 `email`이 아니라 기본값 **`username`**(여기에 이메일을 담는다), `application/x-www-form-urlencoded`.
- 로그인/회원가입/refresh POST도 **CSRF 대상**이다. `GET /api/auth/csrf-token`으로 받은 `XSRF-TOKEN` 쿠키 값을 `X-XSRF-TOKEN` 헤더로 되돌린다(GET 조회에는 CSRF 불필요).
- access 토큰은 10분 만료. `getSession()`이 발급 후 `REFRESH_AFTER_MS`가 지나면 `refresh()`로 자동 갱신한다(refresh 토큰 쿠키는 VU jar에 있어 VU별 로그인 구조에서만 동작).

## 시나리오별 주의

- **admin-user-read**: ADMIN 계정 1개 + max-account-count=1이라 계정 분산이 불가능하다. setup에서 1회 로그인해 전 VU가 토큰을 공유하므로 10분 이내 프로파일(smoke/load/stress 모두 충족)에서만 안전하다. `-e ADMIN_EMAIL`/`-e ADMIN_PASSWORD` 필요.
- **sse-read**: xk6-sse 빌드 바이너리 필요. RPS가 아니라 동시 연결 유지(constant-vus) 모델이라 config 프로파일을 쓰지 않고 `SSE_CLIENTS`로 연결 수를 스윕한다. SLO는 checks 통과율만 본다.
- **content-browse SPORT**: SPORT 수집은 구현돼 있으나 기본 off라, `bulk-seed.sql`의 `sport_content_count` 시딩이 전제다. 데이터가 없으면 setup이 중단하니 `-e SKIP_SPORT=1`로 건너뛸 수 있다.

## 미구현으로 주석 처리된 부분

| 항목 | 위치 | 해제 조건 |
|---|---|---|
| 비밀번호 초기화 인증 | `scenarios/auth-password-reset.js` 전체 주석 | 백엔드 비밀번호 초기화 API 구현 후 실제 계약에 맞춰 채움 |

(SPORT 필터와 access 토큰 refresh는 이번(#384)에 활성화됐다.)

## 관찰 / 대상 환경

- 부하 중 서버 지표는 Grafana(`http://localhost:3000`, 기본 admin/admin)로 병행 관찰한다. Prometheus가 앱의 `/actuator/prometheus`를 스크레이프한다(#374). CPU/메모리/DB 커넥션풀/GC/SSE emitter 수를 본다.
- 로컬 단일 인스턴스 수치는 신뢰하지 않는다(부하 발생기와 대상이 같은 머신).
- 로컬 분산(#323, `docker-compose.distributed.yml` + nginx)이면 `-e BASE_URL=<nginx 프록시 포트>`.
