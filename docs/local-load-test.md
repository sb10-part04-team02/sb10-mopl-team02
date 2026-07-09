# 로컬 부하테스트 실행 방법

## 구성

k6로 조회(read)와 쓰기(write) 부하를 측정합니다. 스크립트는 `load-test/` 아래에 있습니다.

```text
load-test/
  config/      실행 프로파일(smoke/load/stress)과 threshold
  lib/         BASE_URL, 인증, CSRF 헬퍼
  data/        소량 테스트 계정 목록
  scenarios/   조회 3종, 쓰기 2종
  seed/        대량 시딩 SQL
  seed-users.js  소량 계정 시딩(API 경유)
```

조회 시나리오는 `content-browse.js`, `review-read.js`, `playlist-read.js`입니다.  
쓰기 시나리오는 `review-write.js`, `subscribe-notify.js`입니다.

## 실행 전 준비

k6를 설치합니다.

```bash
brew install k6
```

`.env.example`을 복사해 `.env` 파일을 생성하고, 로컬 환경에 맞는 값을 입력합니다.

```bash
cp .env.example .env
```

DB와 애플리케이션을 실행합니다.

```bash
docker compose --env-file .env up -d
```

```bash
./gradlew bootRun
```

`docker-compose.yml`의 `db` 서비스는 최초 기동 시 `src/main/resources/01_schema_v8.sql`을 자동으로 실행합니다.  
따라서 스키마를 따로 적용할 필요가 없습니다.

## 조회 부하테스트

콘텐츠 데이터가 없으면 조회 부하에서 병목이 드러나지 않습니다.  
TMDB 수집은 Spring Batch가 아니라 `ApplicationRunner` 방식이므로, `app.tmdb.run-on-startup=true`로 애플리케이션을 기동하면 1회 수집이 실행됩니다.

테스트 계정을 시딩합니다.

```bash
k6 run -e BASE_URL=http://localhost:8080 --iterations 1 --vus 1 load-test/seed-users.js
```

smoke 프로파일로 스크립트가 정상 동작하는지 확인합니다.

```bash
k6 run -e BASE_URL=http://localhost:8080 -e CONFIG=smoke \
  load-test/scenarios/content-browse.js
```

목표 RPS를 유지하며 부하를 겁니다.

```bash
k6 run -e BASE_URL=http://localhost:8080 -e CONFIG=load -e TARGET_RPS=50 \
  load-test/scenarios/content-browse.js
```

RPS를 점증시켜 한계점을 탐색합니다.

```bash
k6 run -e BASE_URL=http://localhost:8080 -e CONFIG=stress -e PEAK_RPS=300 \
  load-test/scenarios/content-browse.js
```

시나리오 파일만 바꾸면 `review-read.js`, `playlist-read.js`에 동일한 프로파일을 적용할 수 있습니다.

## 쓰기 부하테스트

쓰기는 조회와 달리 대량 시딩과 환경 격리가 필요합니다.  
쓰기 경합과 인덱스 병목은 데이터가 쌓여야 드러납니다.

### 대량 시딩

`load-test/seed/bulk-seed.sql`은 로그인 가능한 계정을 수천 개 생성합니다.  
비밀번호 평문(`loadtest1234`)이 저장소에 공개되어 있으므로, 로컬 또는 부하 전용 DB에서만 실행합니다.

```bash
docker compose --env-file .env exec -T db \
  psql -U "$DB_USERNAME" -d "$DB_NAME" \
  -v user_count=2000 -v content_count=500 -v reviews_per_content=50 \
  < load-test/seed/bulk-seed.sql
```

다음과 같이 출력되면 정상입니다.

```text
  table   | count
----------+-------
 users    |  2000
 contents |   500
 reviews  | 25000
```

계정 2000개, 콘텐츠 500개, 리뷰 25000건이 약 1초에 생성됩니다.  
같은 명령을 다시 실행해도 데이터가 중복으로 쌓이지 않습니다.

`reviews_per_content`는 `user_count`를 넘을 수 없습니다.  
콘텐츠당 리뷰 작성자를 서로 다른 계정으로 배정하기 때문입니다.  
초과하면 다음과 같이 중단됩니다.

```text
ERROR: reviews_per_content 는 user_count 를 넘을 수 없습니다.
```

### 평점 재집계 경합 측정

리뷰를 생성한 뒤 즉시 삭제하는 사이클을 반복합니다.  
리뷰 생성과 삭제는 매번 `ContentRatingService.refreshAggregate`를 호출하고, 이는 `contents` 테이블에 대한 단일 UPDATE입니다.

콘텐츠 1개에 리뷰를 집중시켜 row-level lock 경합을 최대화합니다.

```bash
k6 run -e BASE_URL=http://localhost:8080 -e CONFIG=load -e FOCUS_CONTENTS=1 \
  load-test/scenarios/review-write.js
```

콘텐츠 100개로 분산시켜 경합이 없는 기준선을 측정합니다.

```bash
k6 run -e BASE_URL=http://localhost:8080 -e CONFIG=load -e FOCUS_CONTENTS=100 \
  load-test/scenarios/review-write.js
```

두 실행의 `review-create` p95 차이가 경합 비용입니다.

`FOCUS_CONTENTS`의 상한은 100입니다.  
`ContentSearchRequest.normalizedLimit()`이 `MAX_LIMIT=100`으로 보정하므로 그 이상을 지정해도 100건만 조회됩니다.

`ACCOUNTS`는 실행 프로파일의 `maxVUs` 이상이어야 합니다.  
기본값 200은 `load` 프로파일의 `maxVUs`와 같습니다.  
작으면 VU 인덱스가 순환해 같은 계정이 같은 콘텐츠에 동시에 리뷰를 시도하고, 409로 실패해 삭제 요청을 건너뜁니다.  
이 경우 `review-create` p95는 낮게, `review-delete` 호출 수는 적게 측정됩니다.  
스크립트는 실행 전에 이 조건을 검사해 중단합니다.

`stress` 프로파일은 `maxVUs`가 500이므로 계정도 500개가 필요합니다.

```bash
k6 run -e BASE_URL=http://localhost:8080 -e CONFIG=stress -e ACCOUNTS=500 \
  load-test/scenarios/review-write.js
```

다만 1차 측정 전에는 쓰기와 `stress`를 함께 사용하지 않습니다.  
`stress`는 threshold 초과 시점을 한계점으로 판정하는데, 쓰기 threshold의 p95는 아직 자리표시자이므로 한계점이 임의의 값이 됩니다.  
`smoke` 또는 `load`로 먼저 측정해 `config/write-thresholds.js`를 채운 뒤 사용합니다.

### 알림 팬아웃 측정

구독자가 있는 플레이리스트에 콘텐츠를 추가하면 구독자 전원에게 알림이 발행됩니다.

```bash
k6 run -e BASE_URL=http://localhost:8080 -e SUBSCRIBERS=100 \
  load-test/scenarios/subscribe-notify.js
```

`SUBSCRIBERS`를 0, 10, 100, 500으로 올려가며 `playlist-add-content`의 p95 변화를 확인합니다.  
`playlist-remove-content`는 알림을 발행하지 않으므로 대조군이 됩니다.  
두 태그의 p95 차이가 팬아웃 비용입니다.

계정 0번을 플레이리스트 소유자로 사용하므로, 시딩된 `user_count`는 `SUBSCRIBERS + 1` 이상이어야 합니다.

알림 팬아웃은 비동기가 아니라 동기입니다.  
`PlaylistContentAddedEventListener`에는 `@TransactionalEventListener(AFTER_COMMIT)`만 붙어 있고 `@Async`가 없으며, 코드베이스에 `@EnableAsync`도 없습니다.  
또한 `@Transactional(propagation = REQUIRES_NEW)`가 함께 붙어 있어 커밋 이후 커넥션을 하나 더 획득합니다.

```text
POST /api/playlists/{id}/contents/{contentId}
-> 요청 트랜잭션 커밋
-> 같은 요청 스레드에서 리스너 실행 (커넥션 추가 획득)
-> 구독자 N명 각각에 대해 SELECT + INSERT + SSE 전송
-> 응답 반환
```

따라서 응답시간이 구독자 수에 선형 비례합니다.  
이 시나리오는 팬아웃이 응답시간에 기여하는 몫을 측정합니다.  
커넥션 풀 고갈은 측정하지 않습니다. VU가 1이라 동시 요청이 발생하지 않기 때문입니다.

`SUBSCRIBERS`가 크면 setup이 오래 걸립니다.  
구독자 1명당 로그인(BCrypt)이 필요하므로 500명이면 수십 초에서 수 분이 소요됩니다.  
소유자의 access token은 setup에서 한 번 발급해 재사용하고 수명은 10분이므로, setup 시간과 부하 시간의 합이 10분에 가까워지면 부하 도중 401이 발생합니다.  
스크립트가 잔여 수명을 계산해 경고하지만 자동으로 재발급하지는 않습니다.

## 회차 간 비교

부하가 남긴 데이터는 자동으로 정리되지 않습니다.

```text
reviews            소프트 삭제라 행이 남음. 디스크만 증가
notifications      콘텐츠 추가마다 구독자 수만큼 순증
playlist_contents  하드 삭제라 순환. 누적 없음
```

`reviews` 누적은 재집계 성능에 영향을 주지 않습니다.  
`refreshRatingAggregate`의 집계 대상은 `deleted_at IS NULL`인 행이고, `ix_reviews_content_created_id`와 `ix_reviews_content_rating_id`가 부분 인덱스라 삭제된 행은 인덱스에서 제외되기 때문입니다.

반면 `notifications`는 계속 쌓입니다.  
따라서 회차 간 수치를 비교하려면 회차마다 데이터베이스를 초기화합니다.

```bash
docker compose --env-file .env down -v
```

```bash
docker compose --env-file .env up -d
```

이후 대량 시딩을 다시 실행합니다.

## 중복 제약 응답 처리

k6는 4xx 응답을 기본적으로 실패로 집계합니다.  
중복 제약이 정상 동작한 응답은 서버 오류가 아니므로 `responseCallback`으로 성공 처리합니다.

```text
리뷰 중복(유저 x 콘텐츠)  409
구독 중복                 400
팔로우 중복               400
```

401, 403, 500은 그대로 실패로 집계됩니다.

## 참고

로컬 단일 인스턴스 수치는 부하 발생기와 대상이 같은 머신이므로 그대로 신뢰하지 않습니다.  
로컬 분산 환경을 대상으로 하려면 Nginx 프록시 포트를 사용합니다.

```bash
k6 run -e BASE_URL=http://localhost:8080 -e CONFIG=load \
  load-test/scenarios/content-browse.js
```

부하 중 서버 지표(CPU, 메모리, DB 커넥션 풀, GC)는 actuator로 함께 확인합니다.

- [로컬 분산 환경 실행 방법](local-distributed.md)
- [로컬 Redis/Kafka 검증 방법](local-redis-kafka.md)
