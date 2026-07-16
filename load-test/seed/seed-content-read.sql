-- ============================================================================
--  콘텐츠 목록 조회 부하테스트용 더미 데이터 시딩. 로컬/부하 전용 DB 에서만 실행한다.
--
--  시딩 범위와 근거 (목록 조회 GET /api/contents 가 실제로 타는 쿼리 기준):
--    - contents      : 본 쿼리/totalCount 대상. 3종 타입(MOVIE/TV_SERIES/SPORT) 분포,
--                      created_at 을 과거 2년에 분산(createdAt 커서가 tie 범벅이 되지 않게).
--    - average_rating/review_count : contents 의 반정규화 컬럼이라 리뷰 행 없이 컬럼 값만 채운다.
--    - tags          : 목록 한 페이지마다 IN 일괄 조회 1번 + 응답 페이로드에 포함 -> 콘텐츠당 0~3개.
--    - watching_sessions : watcherCount 는 매 요청 실시간 집계(기본 정렬이자 상관 서브쿼리 병목 후보).
--                      비어 있으면 기본 정렬이 실제보다 싸게 측정되므로 활성 세션을 인기 편중으로 심는다.
--    - users         : watching_sessions 의 FK 대상일 뿐 로그인용이 아니다.
--                      password 가 BCrypt 형식이 아니고 is_locked=TRUE 라 이 계정들로는 로그인이 불가능하다.
--                      (부하 실행용 로그인 계정은 data/users.json + seed-users.js 로 따로 만든다)
--
--  스키마 근거: src/main/resources/db/migration/V1__init.sql (Flyway)
--    - PK 는 전부 UUID(앱 생성이라 DB 기본값 없음) -> gen_random_uuid() (PG13+ 내장)
--    - contents.source/external_id 는 둘 다 NULL(수동 생성 취급, chk pair 제약 만족)
--    - uk_watching_sessions_content_user(content_id, user_id) WHERE deleted_at IS NULL
--    - tags 테이블에는 updated_at 이 없다
--
--  실행 (docker compose 로 띄운 로컬 DB, 서비스명 db):
--    docker compose --env-file .env exec -T db \
--      sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"' \
--      < load-test/seed/seed-content-read.sql
--
--    규모 조정: -v user_count=2000 -v movie_count=5000 -v tv_count=2000 -v sport_count=1000
--
--  정리: docker compose --env-file .env down -v (볼륨 삭제)가 가장 확실. 부분 정리는 하단 CLEANUP 참고.
-- ============================================================================

-- 에러 나면 즉시 중단
\set ON_ERROR_STOP on

-- psql -v 로 안 넘기면 쓰는 기본값
\if :{?user_count}  \else \set user_count 1000  \endif
\if :{?movie_count} \else \set movie_count 3000 \endif
\if :{?tv_count}    \else \set tv_count 1500    \endif
\if :{?sport_count} \else \set sport_count 500  \endif

-- 전체를 한 트랜잭션으로 묶어서 중간 실패 시 롤백
BEGIN;

-- ----------------------------------------------------------------------------
-- 1) FK 전용 더미 유저: loaduser0@mopl.test ~ loaduser{N-1}@mopl.test
--    로그인 불가(password 가 BCrypt 형식이 아님 + is_locked=TRUE). watching_sessions 의 user_id 대상.
-- ----------------------------------------------------------------------------
INSERT INTO users (id, created_at, updated_at, name, email, password, role, is_locked)
SELECT gen_random_uuid(),
       now(),
       now(),
       'loaduser' || i,
       'loaduser' || i || '@mopl.test',
       'load-test-no-login',
       'USER',
       TRUE
FROM generate_series(0, :user_count - 1) AS i
-- 재실행 안전(uk_users_email 이 deleted_at IS NULL 부분 유니크라 ON CONFLICT 대신 NOT EXISTS)
WHERE NOT EXISTS (
    SELECT 1 FROM users u
    WHERE u.email = 'loaduser' || i || '@mopl.test' AND u.deleted_at IS NULL
);

-- ----------------------------------------------------------------------------
-- 2) 콘텐츠: 3종 타입 분포. created_at/updated_at 은 과거 2년 랜덤 분산,
--    average_rating 0.0~5.0(소수 1자리 -> rate 정렬에서 tie-break 경로도 밟게),
--    review_count 0~500 랜덤.
-- ----------------------------------------------------------------------------
INSERT INTO contents (id, created_at, updated_at, content_type, title, description,
                      thumbnail_url, average_rating, review_count)
SELECT gen_random_uuid(),
       g.ts,
       g.ts,
       g.content_type,
       g.title,
       'k6 콘텐츠 목록 조회 부하테스트용 더미 콘텐츠',
       'https://example.test/thumb.jpg',
       round((random() * 5)::numeric, 1)::double precision,
       floor(random() * 501)::int
FROM (
    SELECT 'MOVIE' AS content_type,
           'loadtest movie ' || i AS title,
           now() - (random() * interval '730 days') AS ts
    FROM generate_series(1, :movie_count) AS i
    UNION ALL
    SELECT 'TV_SERIES',
           'loadtest tv ' || i,
           now() - (random() * interval '730 days')
    FROM generate_series(1, :tv_count) AS i
    UNION ALL
    SELECT 'SPORT',
           'loadtest sport ' || i,
           now() - (random() * interval '730 days')
    FROM generate_series(1, :sport_count) AS i
) g
-- 재실행 안전(멱등). title 이 시딩 식별자 역할을 한다.
WHERE NOT EXISTS (
    SELECT 1 FROM contents c
    WHERE c.title = g.title AND c.deleted_at IS NULL
);

-- ----------------------------------------------------------------------------
-- 3) 태그: 콘텐츠당 0~3개(콘텐츠 순번 % 4, 결정적). 이름은 10개 풀에서 회전.
-- ----------------------------------------------------------------------------
INSERT INTO tags (id, created_at, content_id, name)
SELECT gen_random_uuid(),
       now(),
       c.id,
       (ARRAY['action','drama','comedy','thriller','romance','sf','fantasy','sports','live','docu'])
           [1 + (c.c_idx + k) % 10]
FROM (
    SELECT id, row_number() OVER (ORDER BY title) - 1 AS c_idx
    FROM contents
    WHERE title LIKE 'loadtest %' AND deleted_at IS NULL
) c
CROSS JOIN LATERAL generate_series(1, c.c_idx % 4) AS k  -- 0개면 series 가 비어 행이 안 생긴다
-- 재실행 안전: 태그가 하나라도 있는 콘텐츠는 건너뛴다(콘텐츠 단위 가드)
WHERE NOT EXISTS (
    SELECT 1 FROM tags t
    WHERE t.content_id = c.id AND t.deleted_at IS NULL
);

-- ----------------------------------------------------------------------------
-- 4) 활성 시청 세션: watcherCount 집계 대상. 인기 편중 분배.
--    md5(id) 순위 rank 에 대해 floor(user_count / rank) 명이 시청 중
--    -> 상위 콘텐츠는 수백~수천, rank > user_count 인 콘텐츠는 0명(자연 컷오프).
--    타입이 순위에 섞이도록 title 이 아니라 md5(id) 로 순위를 매긴다(id 고정이라 재실행에도 안정).
--    작성자 배정 (rank + j) % user_count 는 한 콘텐츠 안에서 유저가 겹치지 않아
--    uk_watching_sessions_content_user 를 만족한다.
-- ----------------------------------------------------------------------------
INSERT INTO watching_sessions (id, created_at, updated_at, content_id, user_id, joined_at, exited_at)
SELECT gen_random_uuid(),
       now(),
       now(),
       c.id,
       u.id,
       now() - (random() * interval '2 hours'),
       NULL
FROM (
    SELECT id, row_number() OVER (ORDER BY md5(id::text)) AS rank
    FROM contents
    WHERE title LIKE 'loadtest %' AND deleted_at IS NULL
) c
-- 콘텐츠 한 개당 시청자 수를 순위로 결정
CROSS JOIN LATERAL generate_series(0, :user_count / c.rank - 1) AS j
JOIN (
    SELECT id, row_number() OVER (ORDER BY email) - 1 AS u_idx
    FROM users
    WHERE email LIKE 'loaduser%@mopl.test' AND deleted_at IS NULL
) u ON u.u_idx = (c.rank + j) % :user_count
-- 재실행 안전 - (콘텐츠, 유저) 세션이 이미 있으면 건너뜀
WHERE NOT EXISTS (
    SELECT 1 FROM watching_sessions w
    WHERE w.content_id = c.id AND w.user_id = u.id AND w.deleted_at IS NULL
);

COMMIT;

-- 확인
SELECT 'users' AS seeded, count(*) FROM users WHERE email LIKE 'loaduser%@mopl.test' AND deleted_at IS NULL
UNION ALL
SELECT 'contents(MOVIE)',     count(*) FROM contents WHERE title LIKE 'loadtest movie %' AND deleted_at IS NULL
UNION ALL
SELECT 'contents(TV_SERIES)', count(*) FROM contents WHERE title LIKE 'loadtest tv %'    AND deleted_at IS NULL
UNION ALL
SELECT 'contents(SPORT)',     count(*) FROM contents WHERE title LIKE 'loadtest sport %' AND deleted_at IS NULL
UNION ALL
SELECT 'tags',               count(*) FROM tags t
    WHERE EXISTS (SELECT 1 FROM contents c WHERE c.id = t.content_id AND c.title LIKE 'loadtest %')
UNION ALL
SELECT 'watching_sessions',  count(*) FROM watching_sessions ws
    WHERE EXISTS (SELECT 1 FROM contents c WHERE c.id = ws.content_id AND c.title LIKE 'loadtest %');

-- ----------------------------------------------------------------------------
-- CLEANUP (부분 정리 - 볼륨 삭제가 어려울 때만)
--   FK 가 ON DELETE CASCADE 라 contents/users 를 지우면 tags/watching_sessions 도 함께 지워진다.
--
--   DELETE FROM contents WHERE title LIKE 'loadtest %';
--   DELETE FROM users    WHERE email LIKE 'loaduser%@mopl.test';
-- ----------------------------------------------------------------------------
