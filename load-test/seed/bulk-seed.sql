-- ============================================================================
--  경고: 이 스크립트는 로컬 또는 부하테스트 전용 DB 에서만 실행한다.
--
--  아래 users 의 password 는 평문 'loadtest1234' 의 BCrypt 해시다(공개 상수).
--  이 저장소를 읽을 수 있는 사람은 누구나 이 계정들로 로그인할 수 있다는 뜻이다.
--  운영/공유 DB 에 실행하면 즉시 로그인 가능한 계정 수천 개가 생긴다. 절대 금지.
--  (BCrypt 해시 자체는 계정마다 salt 가 달라 역산되지 않는다. 위험한 건 평문이 알려져 있다는 점이다.)
-- ============================================================================
--
-- 목적
--   쓰기 부하테스트(scenarios/review-write.js, scenarios/subscribe-notify.js)용
--   운영급 데이터를 채운다. 조회 부하(#322)가 여유로웠던 건 데이터가 적어서일 수 있고,
--   쓰기 경합/인덱스 병목은 데이터가 쌓여야 드러난다.
--
-- 왜 SQL 인가
--   API 경유(seed-users.js)는 계정마다 BCrypt(strength 10, ~100ms)를 태워 수천 건이면 수 분이 걸리고,
--   그 CPU 를 대상 서버가 쓴다. SQL 은 해시를 상수로 박아 초 단위로 끝난다.
--   대신 소수 계정(data/users.json)은 기존 seed-users.js 를 그대로 쓴다(이원화).
--
-- 스키마 근거: src/main/resources/db/migration/V1__init.sql (Flyway, #378)
--   - 모든 PK 는 UUID (앱이 생성하므로 DB 기본값 없음) -> gen_random_uuid() 사용(PG13+ 내장, 확장 불필요)
--   - updated_at 은 NOT NULL 이고 기본값이 없다 -> 명시적으로 채운다
--   - uk_users_email 은 deleted_at IS NULL 부분 유니크 인덱스
--   - uk_reviews_user_content(author_id, content_id) WHERE deleted_at IS NULL
--
-- 실행
--   psql "$DATABASE_URL" -v user_count=2000 -v content_count=500 -v reviews_per_content=50 \
--     -f load-test/seed/bulk-seed.sql
--
--   docker compose 로 띄운 로컬 DB 라면 (서비스명은 postgres 가 아니라 db):
--   docker compose --env-file .env exec -T db psql -U "$DB_USERNAME" -d "$DB_NAME" \
--     -v user_count=2000 -v content_count=500 -v reviews_per_content=50 \
--     < load-test/seed/bulk-seed.sql
--
-- 정리
--   docker compose --env-file .env down -v (볼륨 삭제)로 통째로 날리는 게 가장 확실하다.
--   부분 정리는 이 파일 하단의 CLEANUP 주석 참고.
--
-- 실행 절차 전체: load-test/README.md

\set ON_ERROR_STOP on

-- psql -v 로 안 넘기면 쓰는 기본값
\if :{?user_count}         \else \set user_count 2000         \endif
\if :{?content_count}      \else \set content_count 500       \endif
\if :{?reviews_per_content}\else \set reviews_per_content 50  \endif
\if :{?sport_content_count}\else \set sport_content_count 100 \endif

-- 사전 조건: reviews_per_content <= user_count
--   콘텐츠 c 의 j 번째 리뷰 작성자를 (c_idx + j) % user_count 로 배정하므로,
--   reviews_per_content 가 user_count 를 넘으면 j 가 한 바퀴 돌아 같은 (author, content) 조합이 생기고
--   uk_reviews_user_content 유니크 인덱스에 걸려 트랜잭션이 통째로 실패한다.
--   NOT EXISTS 가드는 "이미 커밋된 행"만 보므로 같은 INSERT 문 안의 중복은 막지 못한다.
--   원시 유니크 위반 에러 대신 여기서 이유를 밝히고 멈춘다.
--   (psql 은 $$...$$ 안의 :var 를 치환하지 않으므로 DO 블록이 아니라 \if 메타명령을 쓴다)
SELECT (:reviews_per_content > :user_count) AS bad_review_ratio \gset
\if :bad_review_ratio
    \echo 'ERROR: reviews_per_content 는 user_count 를 넘을 수 없습니다.'
    \echo '       콘텐츠당 리뷰 작성자를 서로 다른 계정으로 배정하므로(uk_reviews_user_content),'
    \echo '       reviews_per_content > user_count 이면 같은 (author, content) 조합이 생겨 실패합니다.'
    \quit
\endif

BEGIN;

-- ----------------------------------------------------------------------------
-- 1) 계정: bulk0@mopl.test ~ bulk{N-1}@mopl.test
--    시나리오가 이메일을 인덱스로 계산하므로(seedEmail(i)) 번호는 0 부터 연속이어야 한다.
--    review-write.js 는 VU 마다 다른 계정으로 로그인해 같은 콘텐츠에 서로 다른 author 로
--    동시 INSERT 를 만든다(계정을 공유하면 리뷰 1개 제약 때문에 409 로 튕겨 경합이 안 보인다).
-- ----------------------------------------------------------------------------
INSERT INTO users (id, created_at, updated_at, name, email, password, role, is_locked)
SELECT gen_random_uuid(),
       now(),
       now(),
       'bulk' || i,
       'bulk' || i || '@mopl.test',
       -- 평문 'loadtest1234' 의 BCrypt 해시 (SecurityConfig 의 new BCryptPasswordEncoder(), strength 10)
       '$2a$10$aBNBNmvzx9a.sGbCdNmH0OWBl/vuC0jGLGR4udTVu1qtYTe.xEGUW',
       'USER',
       FALSE
FROM generate_series(0, :user_count - 1) AS i
-- 재실행해도 안전하게(부분 유니크 인덱스라 ON CONFLICT 대상 지정이 불가 -> NOT EXISTS)
WHERE NOT EXISTS (
    SELECT 1 FROM users u
    WHERE u.email = 'bulk' || i || '@mopl.test' AND u.deleted_at IS NULL
);

-- ----------------------------------------------------------------------------
-- 2) 콘텐츠: 리뷰를 몰아넣을 대상.
--    source/external_id 는 둘 다 NULL 로 둔다(chk_contents_source_external_id_pair 를 만족하고,
--    uk_contents_source_external_id 는 NULLS DISTINCT 라 다건 허용).
--    review-write.js 는 createdAt DESC 정렬로 앞에서 FOCUS_CONTENTS 개를 집어가므로
--    방금 넣은 이 콘텐츠들이 선택된다.
-- ----------------------------------------------------------------------------
INSERT INTO contents (id, created_at, updated_at, content_type, title, description,
                      thumbnail_url, average_rating, review_count)
SELECT gen_random_uuid(),
       now(),
       now(),
       'MOVIE',
       'loadtest content ' || i,
       'k6 쓰기 부하테스트용 콘텐츠',
       'https://example.test/thumb.jpg',
       0.0,
       0
FROM generate_series(1, :content_count) AS i
-- 재실행 안전. 이 가드가 없으면 재실행마다 콘텐츠가 중복 생성되고,
-- review-write.js 가 createdAt DESC 로 고르는 대상이 중복본으로 갈려 경합 측정이 희석된다.
WHERE NOT EXISTS (
    SELECT 1 FROM contents c
    WHERE c.title = 'loadtest content ' || i AND c.deleted_at IS NULL
);

-- ----------------------------------------------------------------------------
-- 2b) SPORT 콘텐츠: 조회 부하(content-browse.js)의 typeEqual=SPORT 필터 시나리오용.
--    SPORT 수집(ingestion/sportsdb)은 구현돼 있으나 기본 off(app.ingestion.run-on-startup=false)라
--    외부 API 없이 이 시딩으로 SPORT 데이터를 채운다.
--
--    created_at 을 하루 전으로 미는 이유
--      review-write.js 는 createdAt DESC 상위 FOCUS_CONTENTS 개를 리뷰 대상으로 집어간다.
--      SPORT 를 now() 로 넣으면 그 대상이 SPORT 로 바뀌어 기존 쓰기 측정과 비교가 깨진다.
--      하루 전으로 밀어 MOVIE 콘텐츠의 선택 순서를 그대로 보존한다.
-- ----------------------------------------------------------------------------
INSERT INTO contents (id, created_at, updated_at, content_type, title, description,
                      thumbnail_url, average_rating, review_count)
SELECT gen_random_uuid(),
       now() - interval '1 day',
       now() - interval '1 day',
       'SPORT',
       'loadtest sport ' || i,
       'k6 조회 부하테스트용 SPORT 콘텐츠',
       'https://example.test/thumb.jpg',
       0.0,
       0
FROM generate_series(1, :sport_content_count) AS i
-- 재실행 안전(멱등).
WHERE NOT EXISTS (
    SELECT 1 FROM contents c
    WHERE c.title = 'loadtest sport ' || i AND c.deleted_at IS NULL
);

-- ----------------------------------------------------------------------------
-- 3) 기존 리뷰: 재집계 UPDATE 가 훑어야 할 활성 리뷰를 미리 쌓는다.
--    refreshRatingAggregate 는 AVG/COUNT 서브쿼리로 활성 리뷰를 전량 재집계하므로,
--    콘텐츠당 리뷰가 많을수록 UPDATE 1회의 비용이 커진다. 이게 쓰기 부하의 핵심 변수다.
--
--    uk_reviews_user_content(author_id, content_id) 제약 때문에 (계정, 콘텐츠) 조합이 겹치면 안 된다.
--    -> 콘텐츠 c 의 j 번째 리뷰 작성자를 서로 다른 계정으로 배정한다.
--    reviews_per_content 는 user_count 이하여야 한다.
-- ----------------------------------------------------------------------------
\if :{?skip_reviews} \else
INSERT INTO reviews (id, created_at, updated_at, author_id, content_id, text, rating)
SELECT gen_random_uuid(),
       now(),
       now(),
       u.id,
       c.id,
       'seeded review',
       -- 0.0 ~ 5.0
       round((random() * 5)::numeric, 1)::double precision
FROM (
    SELECT id, row_number() OVER (ORDER BY created_at, id) - 1 AS c_idx
    FROM contents
    WHERE title LIKE 'loadtest content %' AND deleted_at IS NULL
) c
CROSS JOIN LATERAL generate_series(0, :reviews_per_content - 1) AS j
JOIN (
    SELECT id, row_number() OVER (ORDER BY created_at, id) - 1 AS u_idx
    FROM users
    WHERE email LIKE 'bulk%@mopl.test' AND deleted_at IS NULL
) u ON u.u_idx = (c.c_idx + j) % :user_count
-- 재실행 안전
WHERE NOT EXISTS (
    SELECT 1 FROM reviews r
    WHERE r.author_id = u.id AND r.content_id = c.id AND r.deleted_at IS NULL
);

-- 시딩한 리뷰를 콘텐츠 집계에 반영(앱의 refreshRatingAggregate 와 동일한 계산).
-- 이걸 빼먹으면 average_rating/review_count 가 0 인 채로 부하가 시작돼
-- 첫 재집계 UPDATE 만 유난히 커진다.
UPDATE contents c
SET average_rating = COALESCE(agg.avg_rating, 0.0),
    review_count   = COALESCE(agg.cnt, 0)
FROM (
    SELECT content_id, AVG(rating) AS avg_rating, COUNT(*) AS cnt
    FROM reviews
    WHERE deleted_at IS NULL
    GROUP BY content_id
) agg
WHERE c.id = agg.content_id;
\endif

COMMIT;

-- 확인
SELECT 'users'    AS table, count(*) FROM users    WHERE email LIKE 'bulk%@mopl.test' AND deleted_at IS NULL
UNION ALL
SELECT 'contents',          count(*) FROM contents WHERE title LIKE 'loadtest content %' AND deleted_at IS NULL
UNION ALL
SELECT 'sport',             count(*) FROM contents WHERE title LIKE 'loadtest sport %'   AND deleted_at IS NULL
UNION ALL
SELECT 'reviews',           count(*) FROM reviews  WHERE deleted_at IS NULL;

-- ----------------------------------------------------------------------------
-- CLEANUP (부분 정리 — 볼륨 삭제가 어려울 때만)
--   FK 가 ON DELETE CASCADE 라 users/contents 를 지우면 reviews/subscriptions 도 함께 지워진다.
--
--   DELETE FROM contents WHERE title LIKE 'loadtest content %';
--   DELETE FROM contents WHERE title LIKE 'loadtest sport %';
--   DELETE FROM users    WHERE email LIKE 'bulk%@mopl.test';
-- ----------------------------------------------------------------------------
