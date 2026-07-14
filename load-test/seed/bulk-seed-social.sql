-- ============================================================================
--  경고: 이 스크립트는 로컬 또는 부하테스트 전용 DB 에서만 실행한다.
--
--  bulk-seed.sql 이 만든 로그인 가능한 계정(bulk*@mopl.test, 평문 공개)을 그대로 쓴다.
--  운영/공유 DB 에 실행하면 실계정 사이에 대량의 가짜 소셜 그래프가 섞인다. 절대 금지.
-- ============================================================================
--
-- 목적
--   bulk-seed.sql 은 users / contents / reviews 3개 테이블만 채운다.
--   조회 부하(#322)와 쓰기 부하(#337)가 현실적인 분포를 겪으려면
--   나머지 소셜 도메인도 운영급으로 쌓여 있어야 한다. 이 파일이 그 공백을 채운다.
--     - playlists            : 플리 목록/상세 조회(정렬 updatedAt|subscribeCount)
--     - playlist_contents    : 플리 상세(담긴 콘텐츠) 조회
--     - playlist_subscriptions + playlists.subscriber_count : 구독 팬아웃 기준선, subscribedByMe
--     - follows              : 팔로우 목록/피드, 신규 팔로워 알림
--     - notifications        : 알림 목록/SSE 커서 조회
--     - conversations / conversation_members / direct_messages : 대화 목록, DM 히스토리
--
-- 선행 조건
--   반드시 bulk-seed.sql 을 먼저 실행해 bulk 계정/콘텐츠를 만들어 둔다.
--   이 파일은 그 계정/콘텐츠 위에 소셜 그래프를 얹는다. 없으면 상단에서 멈춘다.
--
-- 왜 SQL 인가
--   bulk-seed.sql 과 같은 이유. API 경유는 도메인마다 트랜잭션/알림 팬아웃을 태워
--   수만 건이면 수 분이 걸리고 그 CPU 를 대상 서버가 쓴다. SQL 은 초 단위로 끝난다.
--
-- 스키마 근거: src/main/resources/db/migration/V1__init.sql
--   - 모든 PK 는 UUID (앱이 생성하므로 DB 기본값 없음) -> gen_random_uuid() 사용(PG13+ 내장)
--   - playlists.updated_at / conversation_members.updated_at 은 NOT NULL, 기본값 없음 -> 명시
--   - direct_messages.sender_id / receiver_id 는 users 가 아니라 conversation_members(id) 를 참조
--   - 부분 유니크 인덱스(deleted_at IS NULL): follows / playlist_subscriptions /
--     playlist_contents / conversation_members -> 재실행 안전을 위해 NOT EXISTS 가드
--   - notifications / conversations / direct_messages 는 자연키가 없어 title/content 표식으로 멱등 처리
--
-- 실행
--   psql "$DATABASE_URL" \
--     -v playlist_count=1000 -v contents_per_playlist=20 -v subscriptions_per_playlist=30 \
--     -v follows_per_user=20 -v notifications_per_user=30 \
--     -v conversation_count=1000 -v messages_per_conversation=20 \
--     -f load-test/seed/bulk-seed-social.sql
--
--   docker compose 로 띄운 로컬 DB 라면 (서비스명은 db):
--   docker compose --env-file .env exec -T db psql -U "$DB_USERNAME" -d "$DB_NAME" \
--     < load-test/seed/bulk-seed-social.sql
--
-- 정리
--   docker compose --env-file .env down -v (볼륨 삭제)로 통째로 날리는 게 가장 확실하다.
--   부분 정리는 이 파일 하단의 CLEANUP 주석 참고.
--
-- 실행 절차 전체: load-test/README.md

\set ON_ERROR_STOP on

-- psql -v 로 안 넘기면 쓰는 기본값
\if :{?playlist_count}            \else \set playlist_count 1000            \endif
\if :{?contents_per_playlist}     \else \set contents_per_playlist 20      \endif
\if :{?subscriptions_per_playlist}\else \set subscriptions_per_playlist 30 \endif
\if :{?follows_per_user}          \else \set follows_per_user 20           \endif
\if :{?notifications_per_user}    \else \set notifications_per_user 30      \endif
\if :{?conversation_count}        \else \set conversation_count 1000        \endif
\if :{?messages_per_conversation} \else \set messages_per_conversation 20   \endif

-- ----------------------------------------------------------------------------
-- 선행 데이터 규모를 DB 에서 직접 읽는다(파라미터가 아니라 실제 존재하는 행 수).
-- bulk-seed.sql 을 어떤 파라미터로 돌렸든 여기서 다시 세므로 모듈러 배정이 항상 맞는다.
-- ----------------------------------------------------------------------------
SELECT count(*) AS bulk_user_count
FROM users WHERE email LIKE 'bulk%@mopl.test' AND deleted_at IS NULL \gset
SELECT count(*) AS bulk_content_count
FROM contents WHERE title LIKE 'loadtest content %' AND deleted_at IS NULL \gset

-- 선행 조건 1: bulk 계정/콘텐츠가 있어야 한다(대화방은 서로 다른 두 계정이 필요하므로 최소 2).
SELECT (:bulk_user_count < 2) AS no_users \gset
\if :no_users
    \echo 'ERROR: bulk 계정이 부족합니다(최소 2). 먼저 load-test/seed/bulk-seed.sql 을 실행하세요.'
    \quit
\endif
SELECT (:bulk_content_count < 1) AS no_contents \gset
\if :no_contents
    \echo 'ERROR: loadtest 콘텐츠가 없습니다. 먼저 load-test/seed/bulk-seed.sql 을 실행하세요.'
    \quit
\endif

-- 선행 조건 2: 콘텐츠당 담기/구독/팔로우 fan-out 이 풀(계정/콘텐츠) 크기를 넘으면
--   같은 (부모, 자식) 조합이 생겨 부분 유니크 인덱스에 걸린다. 여기서 이유를 밝히고 멈춘다.
--   구독/팔로우는 자기 자신을 제외하므로 상한이 (계정 수 - 1) 이다.
SELECT (:contents_per_playlist > :bulk_content_count) AS bad_cpp \gset
\if :bad_cpp
    \echo 'ERROR: contents_per_playlist 는 콘텐츠 수를 넘을 수 없습니다(플리 안 콘텐츠는 서로 달라야 함).'
    \quit
\endif
SELECT (:subscriptions_per_playlist > :bulk_user_count - 1) AS bad_spp \gset
\if :bad_spp
    \echo 'ERROR: subscriptions_per_playlist 는 (계정 수 - 1) 을 넘을 수 없습니다(구독자는 서로 다른 계정, 소유자 제외).'
    \quit
\endif
SELECT (:follows_per_user > :bulk_user_count - 1) AS bad_fpu \gset
\if :bad_fpu
    \echo 'ERROR: follows_per_user 는 (계정 수 - 1) 을 넘을 수 없습니다(팔로위는 서로 다른 계정, 자기 자신 제외).'
    \quit
\endif

BEGIN;

-- ----------------------------------------------------------------------------
-- 1) 플레이리스트: 'loadtest playlist {p}'. 소유자는 계정 p % bulk_user_count.
--    subscriber_count 는 아래 4)에서 구독을 실제로 채운 뒤 재집계한다(여기선 0).
-- ----------------------------------------------------------------------------
INSERT INTO playlists (id, created_at, updated_at, owner_id, title, description, subscriber_count)
SELECT gen_random_uuid(),
       now(),
       now(),
       u.id,
       'loadtest playlist ' || p,
       'k6 부하테스트용 플레이리스트',
       0
FROM generate_series(0, :playlist_count - 1) AS p
JOIN (
    SELECT id, row_number() OVER (ORDER BY created_at, id) - 1 AS u_idx
    FROM users WHERE email LIKE 'bulk%@mopl.test' AND deleted_at IS NULL
) u ON u.u_idx = p % :bulk_user_count
-- 재실행 안전(플리 title 에는 유니크 제약이 없으므로 표식으로 가드)
WHERE NOT EXISTS (
    SELECT 1 FROM playlists pl
    WHERE pl.title = 'loadtest playlist ' || p AND pl.deleted_at IS NULL
);

-- ----------------------------------------------------------------------------
-- 2) 플리에 담긴 콘텐츠: 플리마다 contents_per_playlist 개.
--    콘텐츠 인덱스 (p_idx + j) % bulk_content_count 로 한 플리 안에서는 서로 다른 콘텐츠가 배정된다
--    (uk_playlist_contents_content_playlist). p_idx 는 아래에서 재사용하는 플리 인덱스.
-- ----------------------------------------------------------------------------
INSERT INTO playlist_contents (id, created_at, content_id, playlist_id)
SELECT gen_random_uuid(),
       now(),
       c.id,
       pl.id
FROM (
    SELECT id, owner_id, row_number() OVER (ORDER BY created_at, id) - 1 AS p_idx
    FROM playlists WHERE title LIKE 'loadtest playlist %' AND deleted_at IS NULL
) pl
CROSS JOIN LATERAL generate_series(0, :contents_per_playlist - 1) AS j
JOIN (
    SELECT id, row_number() OVER (ORDER BY created_at, id) - 1 AS c_idx
    FROM contents WHERE title LIKE 'loadtest content %' AND deleted_at IS NULL
) c ON c.c_idx = (pl.p_idx + j) % :bulk_content_count
WHERE NOT EXISTS (
    SELECT 1 FROM playlist_contents x
    WHERE x.content_id = c.id AND x.playlist_id = pl.id AND x.deleted_at IS NULL
);

-- ----------------------------------------------------------------------------
-- 3) 구독: 플리마다 subscriptions_per_playlist 명. 구독자는 서로 다른 계정이어야 하고
--    (uk_playlist_subscriptions_user_playlist), 소유자는 자기 플리를 구독하지 않는다.
--    구독자 인덱스 (p_idx + 1 + k) % bulk_user_count 로 배정하고, 소유자와 겹치는 행은 걸러낸다.
-- ----------------------------------------------------------------------------
INSERT INTO playlist_subscriptions (id, created_at, user_id, playlist_id)
SELECT gen_random_uuid(),
       now(),
       u.id,
       pl.id
FROM (
    SELECT id, owner_id, row_number() OVER (ORDER BY created_at, id) - 1 AS p_idx
    FROM playlists WHERE title LIKE 'loadtest playlist %' AND deleted_at IS NULL
) pl
CROSS JOIN LATERAL generate_series(0, :subscriptions_per_playlist - 1) AS k
JOIN (
    SELECT id, row_number() OVER (ORDER BY created_at, id) - 1 AS u_idx
    FROM users WHERE email LIKE 'bulk%@mopl.test' AND deleted_at IS NULL
) u ON u.u_idx = (pl.p_idx + 1 + k) % :bulk_user_count
WHERE u.id <> pl.owner_id
  AND NOT EXISTS (
      SELECT 1 FROM playlist_subscriptions s
      WHERE s.user_id = u.id AND s.playlist_id = pl.id AND s.deleted_at IS NULL
  );

-- 구독 수를 플리에 반영(앱의 구독 카운트와 동일). 빼먹으면 subscribeCount 정렬/노출이 0 으로 왜곡된다.
UPDATE playlists pl
SET subscriber_count = COALESCE(agg.cnt, 0)
FROM (
    SELECT playlist_id, count(*) AS cnt
    FROM playlist_subscriptions
    WHERE deleted_at IS NULL
    GROUP BY playlist_id
) agg
WHERE pl.id = agg.playlist_id;

-- ----------------------------------------------------------------------------
-- 4) 팔로우: 계정마다 follows_per_user 명을 팔로우. 자기 자신 제외, 팔로위는 서로 다른 계정
--    (uk_follows_follower_followee). 팔로위 인덱스 (u_idx + 1 + f) % bulk_user_count.
-- ----------------------------------------------------------------------------
INSERT INTO follows (id, created_at, follower_id, followee_id)
SELECT gen_random_uuid(),
       now(),
       fr.id,
       fe.id
FROM (
    SELECT id, row_number() OVER (ORDER BY created_at, id) - 1 AS u_idx
    FROM users WHERE email LIKE 'bulk%@mopl.test' AND deleted_at IS NULL
) fr
CROSS JOIN LATERAL generate_series(0, :follows_per_user - 1) AS f
JOIN (
    SELECT id, row_number() OVER (ORDER BY created_at, id) - 1 AS u_idx
    FROM users WHERE email LIKE 'bulk%@mopl.test' AND deleted_at IS NULL
) fe ON fe.u_idx = (fr.u_idx + 1 + f) % :bulk_user_count
WHERE NOT EXISTS (
    SELECT 1 FROM follows x
    WHERE x.follower_id = fr.id AND x.followee_id = fe.id AND x.deleted_at IS NULL
);

-- ----------------------------------------------------------------------------
-- 5) 알림: 계정마다 notifications_per_user 건. 자연키가 없어 title 표식으로 멱등 처리한다.
--    이미 시딩돼 있으면(같은 표식 존재) 통째로 건너뛴다.
--    notification_type 은 6종 enum 을 순환 배정한다.
-- ----------------------------------------------------------------------------
SELECT NOT EXISTS (
    SELECT 1 FROM notifications WHERE title = 'loadtest notification' LIMIT 1
) AS do_notifications \gset
\if :do_notifications
INSERT INTO notifications (id, created_at, receiver_id, title, content, level, notification_type)
SELECT gen_random_uuid(),
       now(),
       u.id,
       'loadtest notification',
       'k6 부하테스트용 알림',
       'INFO',
       (ARRAY['ROLE_UPDATED', 'PLAYLIST_SUBSCRIBED', 'PLAYLIST_CONTENT_ADDED',
              'FOLLOWING_USER_ACTIVITY', 'USER_FOLLOWED', 'DIRECT_MESSAGE_RECEIVED'])[(n % 6) + 1]
FROM (
    SELECT id FROM users WHERE email LIKE 'bulk%@mopl.test' AND deleted_at IS NULL
) u
CROSS JOIN generate_series(0, :notifications_per_user - 1) AS n;
\endif

-- ----------------------------------------------------------------------------
-- 6) 대화방 + 멤버 + DM: DM 은 users 가 아니라 conversation_members(id) 를 sender/receiver 로 참조하므로
--    한 문장 안에서 세 테이블의 UUID 를 상관지어 넣어야 한다.
--    plan CTE(MATERIALIZED)로 대화방/멤버 UUID 를 한 번만 생성(볼라틸 gen_random_uuid 재평가 방지)하고,
--    data-modifying CTE 로 conversations -> conversation_members -> direct_messages 를 같은 문장에서 채운다.
--    (FK 는 문장 종료 시점 AFTER 트리거로 검사되므로 CTE 간 삽입 순서와 무관하게 통과한다.)
--
--    대화방 c 의 두 참여자: 계정 (2c) % N 과 (2c+1) % N (N>=2 이므로 서로 다름).
--    자연키가 없어 DM content 표식으로 멱등 처리한다.
-- ----------------------------------------------------------------------------
SELECT NOT EXISTS (
    SELECT 1 FROM direct_messages WHERE content LIKE 'seeded dm %' LIMIT 1
) AS do_dms \gset
\if :do_dms
WITH plan AS MATERIALIZED (
    SELECT g AS c_idx,
           gen_random_uuid() AS conv_id,
           gen_random_uuid() AS mem_a,
           gen_random_uuid() AS mem_b,
           ua.id AS user_a,
           ub.id AS user_b
    FROM generate_series(0, :conversation_count - 1) AS g
    JOIN (
        SELECT id, row_number() OVER (ORDER BY created_at, id) - 1 AS u_idx
        FROM users WHERE email LIKE 'bulk%@mopl.test' AND deleted_at IS NULL
    ) ua ON ua.u_idx = (2 * g) % :bulk_user_count
    JOIN (
        SELECT id, row_number() OVER (ORDER BY created_at, id) - 1 AS u_idx
        FROM users WHERE email LIKE 'bulk%@mopl.test' AND deleted_at IS NULL
    ) ub ON ub.u_idx = (2 * g + 1) % :bulk_user_count
),
ins_conv AS (
    INSERT INTO conversations (id, created_at)
    SELECT conv_id, now() FROM plan
    RETURNING id
),
ins_mem AS (
    INSERT INTO conversation_members (id, created_at, updated_at, conversation_id, member_id, last_read_at)
    SELECT mem_a, now(), now(), conv_id, user_a, now() FROM plan
    UNION ALL
    SELECT mem_b, now(), now(), conv_id, user_b, now() FROM plan
    RETURNING id
)
INSERT INTO direct_messages (id, created_at, conversation_id, sender_id, receiver_id, content)
SELECT gen_random_uuid(),
       now(),
       conv_id,
       CASE WHEN m % 2 = 0 THEN mem_a ELSE mem_b END,
       CASE WHEN m % 2 = 0 THEN mem_b ELSE mem_a END,
       'seeded dm ' || m
FROM plan
CROSS JOIN generate_series(0, :messages_per_conversation - 1) AS m;
\endif

COMMIT;

-- 확인
SELECT 'playlists'              AS table, count(*) FROM playlists              WHERE title LIKE 'loadtest playlist %' AND deleted_at IS NULL
UNION ALL
SELECT 'playlist_contents',              count(*) FROM playlist_contents      WHERE deleted_at IS NULL
UNION ALL
SELECT 'playlist_subscriptions',         count(*) FROM playlist_subscriptions WHERE deleted_at IS NULL
UNION ALL
SELECT 'follows',                        count(*) FROM follows                WHERE deleted_at IS NULL
UNION ALL
SELECT 'notifications',                  count(*) FROM notifications          WHERE title = 'loadtest notification'
UNION ALL
SELECT 'conversations',                  count(*) FROM conversations
UNION ALL
SELECT 'direct_messages',                count(*) FROM direct_messages        WHERE content LIKE 'seeded dm %';

-- ----------------------------------------------------------------------------
-- CLEANUP (부분 정리 - 볼륨 삭제가 어려울 때만)
--   FK 가 ON DELETE CASCADE 라 부모를 지우면 자식도 함께 지워진다.
--   단, direct_messages 는 conversations 를 지우면 CASCADE 로 함께 지워진다.
--
--   DELETE FROM direct_messages WHERE content LIKE 'seeded dm %';
--   DELETE FROM conversations
--     WHERE id IN (SELECT conversation_id FROM conversation_members
--                  WHERE member_id IN (SELECT id FROM users WHERE email LIKE 'bulk%@mopl.test'));
--   DELETE FROM notifications WHERE title = 'loadtest notification';
--   DELETE FROM playlists WHERE title LIKE 'loadtest playlist %';  -- contents/subscriptions CASCADE
--   -- follows 는 bulk 계정 삭제(bulk-seed.sql CLEANUP) 시 CASCADE 로 정리됨
-- ----------------------------------------------------------------------------
