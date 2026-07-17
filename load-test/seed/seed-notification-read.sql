-- 알림 목록 조회 부하테스트용 seed 데이터.
--
-- 사전 조건:
--   load-test/data/users.json의 계정들이 먼저 생성되어 있어야 한다.
--
-- 실행:
--   k6 run -e BASE_URL=http://localhost:8080 --iterations 1 --vus 1 load-test/seed-users.js
--
-- 그 다음:
--   docker compose -f docker-compose.distributed.yml --env-file .env exec -T db \
--     sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"' \
--     < load-test/seed/seed-notification-read.sql
--
-- 알림 개수 조절:
--   docker compose -f docker-compose.distributed.yml --env-file .env exec -T db \
--     sh -c 'psql -v active_notification_count=300 -v deleted_notification_count=3000 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' \
--     < load-test/seed/seed-notification-read.sql

\set ON_ERROR_STOP on

-- psql 실행 시 값을 넘기지 않으면 기본값을 사용한다.
-- active_notification_count: 실제 목록에 노출되는 활성 알림 수
-- deleted_notification_count: 읽음/삭제 처리되어 DB에 누적된 과거 알림 수
\if :{?active_notification_count} \else \set active_notification_count 300 \endif
\if :{?deleted_notification_count} \else \set deleted_notification_count 3000 \endif

BEGIN;

-- loadtest01~03 계정에 활성 알림 데이터를 생성한다.
-- dedup_key를 고정된 규칙으로 넣어두어 같은 SQL을 여러 번 실행해도 중복 생성되지 않게 한다.
INSERT INTO notifications (
    id,
    created_at,
    receiver_id,
    title,
    content,
    level,
    notification_type,
    dedup_key
)
SELECT gen_random_uuid(),
       -- created_at을 조금씩 다르게 만들어 커서 페이지네이션이 실제로 동작하도록 한다.
       now() - ((i * 10 + u.user_idx) * interval '1 second'),
    u.id,
    'k6 notification read seed',
    'k6 notification read load-test message ' || i,
    'INFO',
    'USER_FOLLOWED',
    'LOAD_NOTIFICATION_READ_ACTIVE:' || u.id || ':' || i
FROM (
    SELECT id,
    row_number() OVER (ORDER BY email) AS user_idx
    FROM users
    WHERE email IN (
    'loadtest01@mopl.test',
    'loadtest02@mopl.test',
    'loadtest03@mopl.test'
    )
    AND deleted_at IS NULL
    ) u
    CROSS JOIN generate_series(1, :active_notification_count) AS i
WHERE NOT EXISTS (
    SELECT 1
    FROM notifications n
    WHERE n.receiver_id = u.id
  AND n.dedup_key = 'LOAD_NOTIFICATION_READ_ACTIVE:' || u.id || ':' || i
    );

-- 읽은/삭제 처리된 과거 알림도 사용자별로 충분히 누적시킨다.
-- 실제 서비스에서는 읽은 알림을 물리 삭제하지 않고 deleted_at만 설정하므로,
-- 목록 조회 시 receiver_id 조건 안에 과거 알림이 많이 쌓인 상황을 재현한다.
INSERT INTO notifications (
    id,
    created_at,
    deleted_at,
    receiver_id,
    title,
    content,
    level,
    notification_type,
    dedup_key
)
SELECT gen_random_uuid(),
       now() - ((i * 20 + u.user_idx) * interval '1 second'),
    now() - ((i * 20 + u.user_idx - 5) * interval '1 second'),
    u.id,
    'k6 deleted notification read seed',
    'k6 deleted notification read load-test message ' || i,
    'INFO',
    'USER_FOLLOWED',
    'LOAD_NOTIFICATION_READ_DELETED:' || u.id || ':' || i
FROM (
    SELECT id,
    row_number() OVER (ORDER BY email) AS user_idx
    FROM users
    WHERE email IN (
    'loadtest01@mopl.test',
    'loadtest02@mopl.test',
    'loadtest03@mopl.test'
    )
    AND deleted_at IS NULL
    ) u
    CROSS JOIN generate_series(1, :deleted_notification_count) AS i
WHERE NOT EXISTS (
    SELECT 1
    FROM notifications n
    WHERE n.receiver_id = u.id
  AND n.dedup_key = 'LOAD_NOTIFICATION_READ_DELETED:' || u.id || ':' || i
    );

COMMIT;