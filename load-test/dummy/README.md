# 대용량 더미 데이터 (도메인 전체)

부하 테스트/성능 검증을 위해 도메인 전체(유저 ~ 알림/시청)를 아우르는 대용량 더미 데이터 세트를 순수 SQL(generate_series)로 구현한다.
현행 Flyway 스키마(V1~V3, 더미 대상 14개 테이블)의 제약(부분 유니크, CHECK, FK, 비정규화 카운트)을 모두 만족하며,
행 수가 결정적이라 적재 후 정확한 검증이 가능하다.

- 자동 실행되지 않음 (`spring.sql.init.mode: never`). psql로 수동 적재.
- `load-test/dummy/` 전용(부하 테스트 시딩용). 기존 더미(`dummy_watcher_%`, `dummy_limit_%`, `11111111-`, `22222222-`)와 공존 가능.
- 식별자: 모든 PK가 `d00000XX-0000-4000-8000-...` 형태, 유저 이메일 `dummy_bulk_%@mopl.test`.
- 더미 대상은 도메인 14개 테이블. `redis_outboxes`(Redis 커맨드 아웃박스)와 `batch_*`(배치 메타데이터)는 운영 테이블이라 제외.

## 규모 (SCALE=1.0 기준, 합계 약 540만 행)

| 테이블 | 행 수 | 분포 |
|---|---:|---|
| users | 100,001 (관리자 1 포함) | 균등 |
| social_accounts | 20,000 | GOOGLE/KAKAO 교대 |
| contents | 20,000 | MOVIE 60% / TV_SERIES 30% / SPORT 10% |
| tags | 100,000 | 콘텐츠당 5개 |
| reviews | 약 2,000,000 | 콘텐츠별 Zipf(0.7), 최다 약 3.2만 |
| follows | 약 500,000 | 팔로워별 멱법칙(0.5), 최다 약 790 |
| playlists | 30,000 | - |
| playlist_contents | 375,000 | 플리당 5~20개 |
| playlist_subscriptions | 약 400,000 | 플리별 멱법칙(0.7) |
| conversations / members | 50,000 / 100,000 | 1:1 대화 |
| direct_messages | 약 1,000,000 | 대화별 멱법칙(0.7), 최다 약 1.2만 |
| notifications | 500,000 | 타입 가중(DM 30%, 플리 콘텐츠 25%, ...) |
| watching_sessions | 약 200,000 | 콘텐츠별 멱법칙, 10% 활성 |

시간 분포: users/contents는 과거 24~12개월, 팔로우/플리/대화는 12~6개월, 리뷰/DM/알림 등은 최근 6개월.
비정규화 컬럼(contents.average_rating/review_count, playlists.subscriber_count)은 실제 자식 행과 일치하도록 적재 직후 집계 UPDATE.

## 실행

DB 자격증명은 `.env`에 있으므로, 컨테이너 내부 로컬 접속(trust)을 쓰는 게 간단하다.
컨테이너 이름은 compose 프로젝트명(기본값은 실행 디렉터리명) 기준이라, 저장소 루트(`sb10-mopl-team02`)에서 띄우면 `sb10-mopl-team02-db-1`이다. 다른 디렉터리/worktree에서 띄웠다면 `docker ps`로 실제 이름을 확인해 아래 명령의 컨테이너명을 바꾼다:

```bash
# 1) 파일을 컨테이너로 복사
docker cp load-test/dummy sb10-mopl-team02-db-1:/tmp/dummy

# 2) 풀 규모 적재 (약 540만 행)
docker exec sb10-mopl-team02-db-1 psql -U mopl_user -d mopl_db -f /tmp/dummy/00_load_all.sql

# 3) 검증 (적재와 같은 SCALE 필수)
docker exec sb10-mopl-team02-db-1 psql -U mopl_user -d mopl_db -f /tmp/dummy/90_verify.sql
```

호스트 psql로 직접 붙을 수도 있다: `psql "$DB_URL" -f load-test/dummy/00_load_all.sql` (자격증명은 .env 참고).

### 축소 규모 (스크립트 수정 후 빠른 확인용)

```bash
docker exec sb10-mopl-team02-db-1 psql -U mopl_user -d mopl_db -v SCALE=0.01 -f /tmp/dummy/00_load_all.sql
docker exec sb10-mopl-team02-db-1 psql -U mopl_user -d mopl_db -v SCALE=0.01 -f /tmp/dummy/90_verify.sql
```

SCALE은 유저/콘텐츠/리뷰 등 모든 규모에 곱해진다 (0.01 -> 유저 1천, 약 5.4만 행).

## 로그인

모든 더미 유저의 비밀번호는 `password1!`.

- 일반 유저: `dummy_bulk_0000001@mopl.test` ~ `dummy_bulk_0100000@mopl.test`
- 관리자: `dummy_admin@mopl.test` (role=ADMIN)

해시 재생성이 필요하면 (`10_users.sql`의 `DUMMY_PW_HASH`):

```bash
htpasswd -bnBC 10 "" 'password1!' | cut -d: -f2
```

`$2y$` 형식이지만 Spring Security `BCryptPasswordEncoder`(SecurityConfig.java)가 `$2a/$2b/$2y` 모두 검증한다.

## 재실행 / 정리

- `10_users.sql`의 가드가 더미 존재 시 적재를 중단시킨다. 재적재는 **cleanup 후**에만:

```bash
docker exec sb10-mopl-team02-db-1 psql -U mopl_user -d mopl_db -f /tmp/dummy/01_cleanup.sql
```

- cleanup은 자식 -> 부모 순 PK range DELETE라 더미만 정확히 지우고 실데이터는 건드리지 않는다.
- dev DB를 통째로 초기화할 거면 `docker compose down -v` 후 재기동(Flyway 재적용)이 가장 빠르다.

## 파일 구성

| 파일 | 내용 |
|---|---|
| `_helpers.sql` | SCALE 변수, 성능 SET, 결정적 UUID 함수(pg_temp.duuid) |
| `00_load_all.sql` | 01 -> 10 -> 20 -> 30 -> 40 -> 50 -> 60 순차 실행 + ANALYZE |
| `01_cleanup.sql` | 더미 전체 삭제 |
| `10_users.sql` | users + social_accounts (공통 비밀번호 해시는 이 파일에서 수정) |
| `20_contents.sql` | contents + tags |
| `30_reviews.sql` | reviews + rating 집계 반영 |
| `40_social.sql` | follows + playlists + playlist_contents + subscriptions + 구독 수 집계 |
| `50_dm.sql` | conversations + conversation_members + direct_messages |
| `60_misc.sql` | notifications + watching_sessions |
| `90_verify.sql` | 행 수/집계/규칙/분포 검증 (읽기 전용) |

## 설계 메모

- **결정적 UUID**: `duuid(접두사, n)` = `d00000XX-0000-4000-8000-<n의 hex>`. FK를 조인 없이 산술식으로 재계산하고, cleanup이 PK range scan 1회로 끝난다.
- **유니크 페어**: (작성자 x 콘텐츠) 등은 서로소 stride(99991, 소수)로 뽑아 중복이 수학적으로 불가능. ON CONFLICT 불필요, 행 수 결정적.
- **direct_messages의 sender/receiver는 users가 아니라 conversation_members.id를 참조**한다. 멤버 id를 대화 번호의 함수(2n-1, 2n)로 고정해 해결.
- 소요 시간(풀 규모): 아래 "적재 기록" 참고.

## 적재 기록

2026-07-15, SCALE=1.0 (약 560만 행), 로컬 Docker postgres:16 기준:

| 구간 | 소요 |
|---|---|
| 전체 (cleanup + 적재 + ANALYZE) | 약 3분 49초 |
| 01 cleanup (560만 행 삭제, 임시 인덱스 포함) | 약 30초 |
| 30 reviews 200만 INSERT / rating 집계 UPDATE | 52초 / 7초 |
| 40 follows 50만 / playlist_contents 37.5만 | 11초 / 18초 |
| 50 direct_messages 100만 | 34초 |
| 60 notifications 50만 / watching_sessions 20만 | 13초 / 6초 |
| ANALYZE | 41초 |

검증 결과: 90_verify.sql 행 수 14/14 일치, 집계/규칙 위반 0건.
공통 비밀번호 해시는 Spring Security BCryptPasswordEncoder.matches("password1!", 해시) = true 확인됨.
