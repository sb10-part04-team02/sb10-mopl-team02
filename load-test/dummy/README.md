# 대용량 더미 데이터 (도메인 전체)

부하 테스트/성능 검증을 위해 도메인 전체(유저 ~ 알림/시청)를 아우르는 대용량 더미 데이터 세트를 순수 SQL(generate_series)로 구현한다.
현행 Flyway 스키마(V1~V3, 더미 대상 14개 테이블)의 제약(부분 유니크, CHECK, FK, 비정규화 카운트)을 모두 만족하며,
행 수가 결정적이라 적재 후 정확한 검증이 가능하다.

- 자동 실행되지 않음 (`spring.sql.init.mode: never`). psql로 수동 적재.
- `load-test/dummy/` 전용(부하 테스트 시딩용). 기존 더미(`dummy_watcher_%`, `dummy_limit_%`, `11111111-`, `22222222-`)와 공존 가능.
- 식별자: 모든 PK가 `d00000XX-0000-4000-8000-...` 형태, 유저 이메일 `dummy_bulk_%@mopl.test`.
- 더미 대상은 도메인 14개 테이블. `redis_outboxes`(Redis 커맨드 아웃박스)와 `batch_*`(배치 메타데이터)는 운영 테이블이라 제외.

## 규모 (SCALE=1.0 기준, 합계 약 535만 행)

| 테이블 | 행 수 | 분포 |
|---|---:|---|
| users | 100,001 (관리자 1 포함) | 24개월 전 ~ 현재, 최근일수록 밀도 높은 성장 곡선 |
| social_accounts | 20,000 | GOOGLE/KAKAO 교대 |
| contents | 20,000 | MOVIE 60% / TV_SERIES 30% / SPORT 10% |
| tags | 약 58,000 | MOVIE/TV 2~4개(장르), SPORT 2개(종목+리그) |
| reviews | 약 2,000,000 | 콘텐츠별 Zipf(0.7), 최다 약 3.2만. 평점은 정수 1~5 J-curve |
| follows | 약 500,000 | 팔로워별 멱법칙(0.5), 최다 약 790. 엣지 30%는 셀럽(상위 1%)에게 |
| playlists | 30,000 | - |
| playlist_contents | 375,000 | 플리당 5~20개 |
| playlist_subscriptions | 약 400,000 | 플리별 멱법칙(0.7) |
| conversations / members | 50,000 / 100,000 | 1:1 대화 |
| direct_messages | 약 1,000,000 | 대화별 멱법칙(0.7), 최다 약 1.2만 |
| notifications | 500,000 | 타입 가중(DM 30%, 플리 콘텐츠 25%, ...), 40%는 상위 10% 유저 수신 |
| watching_sessions | 약 200,000 | 콘텐츠별 멱법칙, 10% 활성(최근 4시간 내 참여) |

시간 분포: 모든 행이 "부모 이후 ~ 현재"의 인과를 지키며(`pg_temp.dchild`), 최근일수록 밀도가 높고
시각은 저녁 피크(70%: 17~25시, 피크 21시)로 재배치된다(`pg_temp.dhour`). 유저는 성장 곡선(random^1.5),
콘텐츠는 배치 수집처럼 매일 03시 부근, 활성 시청 세션은 최근 4시간 내, DM은 대화별 활동 종료 시점
(수명의 25~100%, 결정적 해시)까지 등간격이라 일부 대화는 현재까지 활발하다.
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
- **SPORT 콘텐츠 제목**은 영화/드라마와 달리 "리그 N라운드 홈팀 vs 원정팀" 형식으로 생성한다(K리그1/KBO/KBL/LCK, 종목별 8팀 풀). 스포츠 순번 `s=c/10`을 인덱스로 써 4개 리그/8팀이 균등 분포하고 홈 != 원정을 보장한다. 설명은 실수집기(SportsDbEventMapper)의 합성 형식 "{리그} {시즌} - {경기장}(홈팀과 짝) - {경기일}"을 따른다.
- **평점 J-curve**: FE가 정수 별점 1~5만 입력하므로 rating은 정수. 콘텐츠별 품질 계층(명작 20%/평작 50%/범작 20%/졸작 10%, 해시 `(c*37)%20`)에 삼각 노이즈를 더해 콘텐츠 평균이 약 2점대~4점대로 갈린다(rate 정렬 변별력). 리뷰 텍스트는 평점 구간(긍정/중립/부정)과 감성이 일치하는 풀에서 뽑는다.
- **알림 문구**는 각 도메인 EventListener의 실제 템플릿을 그대로 쓰고, 이름/제목은 실존 더미 엔티티를 조인해 채운다. 실서비스 코드는 INFO 레벨만 생성하므로 전부 INFO.
- **셀럽 팔로우**: 팔로워당 엣지의 첫 30%는 유저 1~n_celebs(기본 상위 1%, 하한 241)에게 배정. 두 구간(셀럽/비셀럽)이 겹치지 않고 stride 99991(소수)이 구간 크기와 서로소이며 k 창(<=790)이 구간 크기보다 작아 (follower, followee) 중복이 없다. self-follow는 WHERE로 제외하고 90_verify가 동일 식으로 재계산한다.
- **비상관 LATERAL 함정**: 바깥 변수를 참조하지 않는 LATERAL 서브쿼리 안의 `random()`은 PG가 1회만 평가해 전 행이 같은 값을 갖는다. 시간/평점 LATERAL은 반드시 안쪽 루프 변수(seed 컬럼)를 참조시킬 것. SELECT 리스트의 volatile 함수 호출은 행마다 평가되므로 안전.
- 소요 시간(풀 규모): 아래 "적재 기록" 참고.

## 적재 기록

2026-07-20 리얼리즘 개편(시간 분포/평점 J-curve/실서비스 문구/셀럽 팔로우/타입별 태그) 후
일회용 postgres:16 컨테이너에서 SCALE=0.01, SCALE=1.0 모두 재검증: 행 수 14/14 일치, 집계/규칙 위반 0건.

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
