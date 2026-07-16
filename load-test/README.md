# load-test - k6 부하테스트

부하테스트 베이스다. 실행 프로파일(smoke/load/stress), 인증, 커서 순회, 시딩 절차를
공통 모듈로 깔아뒀다. 새 부하테스트는 이 위에 **시나리오 파일 하나(+ 필요하면 시드 하나)만 얹으면** 된다.
- 개별 시나리오의 여정/전제/고유 env 는 그 **시나리오 파일 상단 주석**에 있다.
- 개별 시드의 시딩 범위/실행법/규모 옵션은 그 **SQL 파일 상단 주석**에 있다.

## 구조

```text
load-test/
  config/           # 실행 프로파일과 SLO (얼마나 부하를 주나)
    thresholds.js   #   전 시나리오 공통 SLO (에러율/검증 통과율 + sign-in). duration 은 무태그 글로벌로 두지 않고 시나리오가 태그로 선언
    smoke.js        #   constant-vus, 1분 sanity
    load.js         #   ramping-arrival-rate, 목표 RPS 유지
    stress.js       #   ramping-arrival-rate, RPS 점증으로 한계점 탐색
    index.js        #   -e CONFIG=smoke|load|stress 선택 + optionsWith(시나리오 태그 SLO 병합)
  lib/              # 재사용 유틸 (어떻게 요청/검증하나)
    http.js         #   BASE_URL, 인증 헤더, CursorResponse check, 커서 순회(fetchCursorPages)
    auth.js         #   CSRF 발급 -> form-login(username=email) -> accessToken (이 프로젝트 인증 함정 캡슐화)
  data/             # 부하 실행용 로그인 계정 (data/users.json + SharedArray 로더)
  scenarios/        # 시나리오별 여정. 새 부하테스트는 여기에 파일을 추가한다
  seed/             # 조회 부하용 대량 더미 데이터 시딩 SQL. 시나리오가 읽는 데이터를 미리 채운다
  seed-users.js     # 부하 실행용 로그인 계정 생성(API 경유, 소량)
```

## 사전 준비 (시나리오 공통, 1회)

### 1. k6 설치

```bash
brew install k6   # 또는 docker run grafana/k6
```

### 2. 인프라 기동

로그인이 Redis 에 의존한다(refresh 토큰 저장). DB 만으로는 로그인 자체가 실패하므로 최소 DB + Redis 가 필요하다.
기본 `docker-compose.yml` 이 db + redis(+ prometheus/grafana)를 띄운다.

```bash
cp .env.example .env   # 최초 1회
docker compose --env-file .env up -d
```

### 3. 앱 기동 (스키마는 Flyway 가 생성)

```bash
./gradlew bootRun
```

### 4. 로그인 계정 시딩 (data/users.json 의 소량 계정, API 경유)

```bash
k6 run -e BASE_URL=http://localhost:8080 --iterations 1 --vus 1 load-test/seed-users.js
```

### 5. 조회 대상 데이터 시딩

빈 DB 로 조회 부하를 걸면 병목이 안 드러난다. 돌릴 시나리오가 읽는 데이터를 `seed/` 의 대응 SQL 로 미리 채운다.
각 SQL 은 멱등(재실행해도 중복이 안 쌓임)이고, 시딩 범위/규모 옵션(`-v ...`)은 파일 상단 주석에 있다.

```bash
# 예: 콘텐츠 조회 시나리오용 시드
docker compose --env-file .env exec -T db \
  sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"' \
  < load-test/seed/seed-content-read.sql
```

## 실행

시나리오 파일만 바꿔 끼우면 된다. 프로파일은 `-e CONFIG` 로 고른다.

```bash
SCENARIO=load-test/scenarios/content-browse.js   # 돌릴 시나리오로 바꾼다

# smoke (기본): 스크립트/시스템 sanity
k6 run -e BASE_URL=http://localhost:8080 -e CONFIG=smoke "$SCENARIO"

# load: 목표 RPS 유지 (-e TARGET_RPS=50)
k6 run -e BASE_URL=http://localhost:8080 -e CONFIG=load -e TARGET_RPS=50 "$SCENARIO"

# stress: RPS 점증으로 한계점 탐색 (-e PEAK_RPS=300)
k6 run -e BASE_URL=http://localhost:8080 -e CONFIG=stress -e PEAK_RPS=300 "$SCENARIO"
```

### arrival-rate 와 여정(journey) 길이

`load`/`stress` 의 `TARGET_RPS`/`PEAK_RPS` 는 **여정(iteration) 시작률**이지 HTTP 요청률이 아니다.
한 여정이 여러 요청을 보내면 서버 실측 RPS 는 대략 `RPS x 여정당 요청 수` 가 된다(여정당 요청 수는 시나리오마다 다르다).
절대 수치를 비교할 때 이 배수를 감안한다.

여정이 길어 arrival-rate 가 VU 를 다 쓰면 `dropped_iterations` 가 생긴다.
**`dropped_iterations` 가 0 이 아니면 목표 부하에 도달하지 못한 것이므로 결과를 신뢰하지 않는다.**
이때는 `-e TARGET_RPS` 를 낮추거나 config 의 maxVUs 를 늘린다.

## 새 시나리오 추가하기

베이스를 재사용하고 아래만 얹으면 된다.

1. `scenarios/<name>.js` 생성. `lib/http.js`(authParams/checkCursorResponse/fetchCursorPages)와 `lib/auth.js`(login)를 임포트해 여정을 짠다.
2. 프로파일 옵션은 `config/index.js` 의 `optionsWith({...})` 로 가져오고, **그 시나리오 고유 태그 SLO 를 인자로 선언**한다(공통 `thresholds.js` 는 건드리지 않는다 - 그래야 다른 시나리오에 노이즈가 안 생긴다).

   ```js
   import { optionsWith } from '../config/index.js';
   export const options = optionsWith({
     'http_req_duration{name:my-tag}': ['p(95)<500'],
   });
   ```

3. 각 요청에 `tags: { name: 'my-tag' }` 를 붙여 엔드포인트별로 지표를 분리한다.
4. 조회 대상 데이터가 필요하면 `seed/<name>.sql` 을 멱등하게 추가하고, 시딩 범위/실행법을 그 파일 상단 주석에 적는다.
5. 시나리오 고유 env 나 전제는 시나리오 파일 상단 주석에 적는다(이 README 는 안 고친다).

## 환경 변수 (공통)

시나리오 고유 변수는 각 시나리오/시드 파일 상단 주석을 참고한다.

| 변수 | 기본값 | 설명 |
|---|---|---|
| `BASE_URL` | `http://localhost:8080` | 대상 앱 주소(로컬 분산이면 nginx 프록시 포트) |
| `CONFIG` | `smoke` | `smoke`\|`load`\|`stress` |
| `TARGET_RPS` | 50 | `load` 의 목표 여정 시작률 |
| `PEAK_RPS` | 300 | `stress` 의 피크 여정 시작률 |

## Grafana 시각화 (#398)

k6 메트릭을 Prometheus remote write 로 밀어넣고, 기존 Grafana 스택에서 실시간으로 본다.
(Prometheus 는 `--web.enable-remote-write-receiver` 로 기동된다 — `docker-compose.yml` 참고)

```bash
# 1. Prometheus + Grafana 기동
docker compose up -d prometheus grafana

# 2. -o experimental-prometheus-rw 를 붙여 실행 (기존 실행 커맨드에 그대로 추가 가능)
K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write \
K6_PROMETHEUS_RW_TREND_STATS="avg,min,max,p(90),p(95),p(99)" \
k6 run -o experimental-prometheus-rw \
  --tag testid=content-browse-$(date +%m%d-%H%M) \
  -e BASE_URL=http://localhost:8080 -e CONFIG=load -e TARGET_RPS=50 \
  load-test/scenarios/content-browse.js
```

- 대시보드: <http://localhost:3000> (admin/admin) → Mopl 폴더 → **k6 부하테스트**
  (`config/monitoring/grafana/dashboards/k6-load-testing.json` 이 자동 프로비저닝된다)
- `/api/v1/write` 는 POST 전용 수신 엔드포인트다. 브라우저로 열면 405 가 뜨는 게 정상이며, 상태 확인은 Prometheus UI(<http://localhost:9090>)로 한다.
- k6 를 Docker 로 돌리면 컨테이너 안에서 `localhost` 는 k6 자신이다. compose 네트워크에 붙여 서비스명으로 접근한다:

  ```bash
  # 리포지토리 루트에서 실행 (시나리오가 ../lib 등을 임포트하므로 load-test 전체를 마운트)
  docker run --rm --network sb10-mopl-team02-dev_default \
    -v "$PWD/load-test:/load-test" \
    -e K6_PROMETHEUS_RW_SERVER_URL=http://prometheus:9090/api/v1/write \
    -e 'K6_PROMETHEUS_RW_TREND_STATS=avg,min,max,p(90),p(95),p(99)' \
    grafana/k6 run -o experimental-prometheus-rw \
    --tag testid=<실행ID> -e BASE_URL=http://host.docker.internal:8080 \
    /load-test/scenarios/content-browse.js
  ```
- `--tag testid=...` 는 실행(run) 구분용 — 대시보드 상단 `Test ID` 변수로 특정 실행만 필터링한다.
  시나리오·프로파일·시각을 담은 값(예: `content-browse-load-0715-1430`)을 권장.
- `K6_PROMETHEUS_RW_TREND_STATS` 를 지정해야 p95/p99 게이지(`k6_http_req_duration_p95` 등)가 생성된다.
  생략하면 기본값 `p(99)`만 남아 대시보드 패널 대부분이 비어 보인다.
- 서버 쪽 지표(CPU/힙/GC/커넥션풀)는 같은 Grafana 의 **Mopl 서버 모니터링** 대시보드로 병행 관찰.

## 이 프로젝트의 인증 함정 (lib/auth.js 가 캡슐화)

- 로그인은 컨트롤러가 아니라 Spring Security formLogin 필터가 처리한다.
- 파라미터명은 DTO 의 `email` 이 아니라 기본값 **`username`** (여기에 이메일을 담는다), `application/x-www-form-urlencoded`.
- 로그인/회원가입 POST 도 **CSRF 대상** -> `GET /api/auth/csrf-token` 으로 받은 `XSRF-TOKEN` 쿠키 값을 `X-XSRF-TOKEN` 헤더로 되돌린다(GET 조회에는 CSRF 불필요).
- access 토큰은 10분 만료. 테스트가 15분 미만이면 setup 에서 1회 로그인 후 전체 VU 가 토큰을 공유하면 된다(k6 권장).
  10분 넘게 도는 프로파일을 쓰면 VU 별 로그인 + `refresh` 도입이 필요하다.

## 관찰 / 대상 환경

- 부하 중 서버 지표는 Grafana(`http://localhost:3000`, 기본 admin/admin)로 병행 관찰한다.
  Prometheus 가 앱의 `/actuator/prometheus` 를 스크레이프한다(#374). CPU/메모리/DB 커넥션풀/GC 를 본다.
- 로컬 단일 인스턴스 수치는 신뢰하지 않는다(부하 발생기와 대상이 같은 머신).
- 로컬 분산(#323, `docker-compose.distributed.yml` + nginx)이면 `-e BASE_URL=<nginx 프록시 포트>`.
- 원격 환경 측정은 CD 파이프라인 완성 후 운영 동일 스펙에서 진행한다.
