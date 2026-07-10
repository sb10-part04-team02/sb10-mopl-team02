# load-test — k6 부하테스트

이슈 #322. 콘텐츠 조회 등 핵심 API에 대한 k6 부하테스트 환경.

## 구조

```text
load-test/
  config/
    thresholds.js   # SLO threshold (p95<500, 에러율<1%)
    smoke.js        # constant-vus, 1분 sanity
    load.js         # ramping-arrival-rate, 목표 RPS 유지
    stress.js       # ramping-arrival-rate, RPS 점증 → 한계점 탐색
    index.js        # -e CONFIG=smoke|load|stress 로 프로파일 선택
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
  seed-users.js     # 테스트 계정 생성 스크립트
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
