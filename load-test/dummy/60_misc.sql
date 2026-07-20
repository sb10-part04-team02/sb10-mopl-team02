-- notifications(50만) + watching_sessions(약 20만)
\ir _helpers.sql

BEGIN;

-- 알림: 타입 가중 순환(DM 6 : 플리콘텐츠 5 : 팔로잉활동 4 : 새팔로워 3 : 플리구독 1 : 권한변경 1 = 20칸).
-- dedup_key = 'dummy:' || n 은 전역 유니크라 uq_notifications_receiver_dedup(V2) 충돌 불가능. 티어 2.
INSERT INTO notifications (id, created_at, deleted_at, receiver_id, title, content, level, notification_type, dedup_key)
SELECT pg_temp.duuid('d000000b', n),
       now() - interval '6 months' + random() * interval '179 days',
       NULL,
       pg_temp.duuid('d0000001', 1 + (n % :n_users)),
       CASE t.v
         WHEN 'DIRECT_MESSAGE_RECEIVED'  THEN '새 DM이 도착했습니다'
         WHEN 'PLAYLIST_CONTENT_ADDED'   THEN '구독한 플레이리스트에 콘텐츠가 추가되었습니다'
         WHEN 'FOLLOWING_USER_ACTIVITY'  THEN '팔로우한 사용자의 새 활동이 있습니다'
         WHEN 'USER_FOLLOWED'            THEN '새 팔로워가 생겼습니다'
         WHEN 'PLAYLIST_SUBSCRIBED'      THEN '내 플레이리스트에 새 구독자가 생겼습니다'
         ELSE '권한이 변경되었습니다'
       END,
       CASE t.v
         WHEN 'DIRECT_MESSAGE_RECEIVED'  THEN '확인하지 않은 메시지가 있습니다.'
         WHEN 'PLAYLIST_CONTENT_ADDED'   THEN '지금 확인해보세요.'
         WHEN 'FOLLOWING_USER_ACTIVITY'  THEN '새 리뷰가 등록되었습니다.'
         WHEN 'USER_FOLLOWED'            THEN '프로필에서 확인해보세요.'
         WHEN 'PLAYLIST_SUBSCRIBED'      THEN '구독자가 늘고 있습니다.'
         ELSE '변경된 권한으로 다시 로그인됩니다.'
       END || ' (no.' || n || ')',
       CASE WHEN t.v = 'ROLE_UPDATED' THEN 'WARNING' ELSE 'INFO' END,
       t.v,
       'dummy:' || n
FROM generate_series(1, :n_notifications) AS n
CROSS JOIN LATERAL (
  SELECT (ARRAY['DIRECT_MESSAGE_RECEIVED','DIRECT_MESSAGE_RECEIVED','DIRECT_MESSAGE_RECEIVED',
                'DIRECT_MESSAGE_RECEIVED','DIRECT_MESSAGE_RECEIVED','DIRECT_MESSAGE_RECEIVED',
                'PLAYLIST_CONTENT_ADDED','PLAYLIST_CONTENT_ADDED','PLAYLIST_CONTENT_ADDED',
                'PLAYLIST_CONTENT_ADDED','PLAYLIST_CONTENT_ADDED',
                'FOLLOWING_USER_ACTIVITY','FOLLOWING_USER_ACTIVITY','FOLLOWING_USER_ACTIVITY','FOLLOWING_USER_ACTIVITY',
                'USER_FOLLOWED','USER_FOLLOWED','USER_FOLLOWED',
                'PLAYLIST_SUBSCRIBED',
                'ROLE_UPDATED'])[1 + (n % 20)] AS v
) t;

-- 시청 세션: 콘텐츠별 멱법칙(인기 편중 유지, 최다 약 3,200명). 10%는 활성(exited_at NULL).
-- 활성 여부는 (c + k) % 10 기준: k만 쓰면 시청 수 10 미만인 꼬리 콘텐츠가 전부 비활성이 되어 비율이 낮아진다.
-- uk_watching_sessions_content_user: 유저 = 1 + ((c*11 + k*99991) % n_users) 로 콘텐츠 내 중복 없음. 티어 2.
WITH w AS (
  SELECT c, power(c::float, -0.7) AS wt FROM generate_series(1, :n_contents) AS c
), tot AS (
  SELECT sum(wt) AS s FROM w
), per_c AS (
  SELECT w.c, LEAST(:n_users, GREATEST(1, round(:n_watch_target * w.wt / tot.s)))::int AS cnt
  FROM w, tot
)
INSERT INTO watching_sessions (id, created_at, updated_at, deleted_at, content_id, user_id, joined_at, exited_at)
SELECT pg_temp.duuid('d000000c', (pc.c::bigint - 1) * :n_users + k),
       ts.t, ts.t, NULL,
       pg_temp.duuid('d0000002', pc.c),
       pg_temp.duuid('d0000001', 1 + ((pc.c::bigint * 11 + k::bigint * 99991) % :n_users)),
       ts.t,
       CASE WHEN (pc.c + k) % 10 = 0 THEN NULL
            ELSE ts.t + (5 + random() * 175) * interval '1 minute' END
FROM per_c pc
CROSS JOIN LATERAL generate_series(1, pc.cnt) AS k
CROSS JOIN LATERAL (SELECT now() - interval '6 months' + random() * interval '179 days' AS t) ts;

COMMIT;
