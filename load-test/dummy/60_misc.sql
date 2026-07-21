-- notifications(50만) + watching_sessions(약 20만)
\ir _helpers.sql

BEGIN;

-- 알림: 타입 가중 순환(DM 6 : 플리콘텐츠 5 : 팔로잉활동 4 : 새팔로워 3 : 플리구독 1 : 권한변경 1 = 20칸).
-- 문구는 각 도메인 EventListener 가 실제로 생성하는 템플릿을 그대로 쓰고, 이름/제목은 실존 더미
-- 엔티티(유저/플리/콘텐츠)를 조인해 채운다. 실서비스 코드는 INFO 레벨만 생성하므로 전부 INFO.
-- 수신자 리얼리즘: 40%(n%5<2)는 상위 10% 활성 유저에게 몰아줘 수신 편중을 만든다.
-- dedup_key = '{TYPE}:{receiverId}:dummy-{n}' 은 전역 유니크라 uq_notifications_receiver_dedup 충돌 불가능.
INSERT INTO notifications (id, created_at, deleted_at, receiver_id, title, content, level, notification_type, dedup_key)
SELECT pg_temp.duuid('d000000b', n),
       ns.t,
       NULL,
       ru.id,
       CASE t.v
         WHEN 'DIRECT_MESSAGE_RECEIVED'  THEN '새 메시지'
         WHEN 'USER_FOLLOWED'            THEN '새 팔로워 알림'
         WHEN 'PLAYLIST_SUBSCRIBED'      THEN '플레이리스트 구독 알림'
         WHEN 'PLAYLIST_CONTENT_ADDED'   THEN '구독 플레이리스트 콘텐츠 추가 알림'
         WHEN 'FOLLOWING_USER_ACTIVITY'  THEN CASE WHEN n % 2 = 0
                                                   THEN au.name || '님이 플레이리스트를 만들었어요.'
                                                   ELSE au.name || '님이 콘텐츠를 시청하기 시작했어요.' END
         ELSE '권한 변경 알림'
       END,
       CASE t.v
         WHEN 'DIRECT_MESSAGE_RECEIVED'  THEN au.name || '님이 메시지를 보냈습니다.'
         WHEN 'USER_FOLLOWED'            THEN au.name || '님이 팔로우했습니다.'
         WHEN 'PLAYLIST_SUBSCRIBED'      THEN au.name || '님이 [' || pl.title || '] 플레이리스트를 구독했습니다.'
         WHEN 'PLAYLIST_CONTENT_ADDED'   THEN '[' || pl.title || '] 플레이리스트에 ' || ct.title || ' 콘텐츠가 추가되었습니다.'
         WHEN 'FOLLOWING_USER_ACTIVITY'  THEN CASE WHEN n % 2 = 0
                                                   THEN '[' || pl.title || '] ' || pl.description
                                                   ELSE '[' || ct.title || '] 시청 중' END
         ELSE '회원님의 권한이 ADMIN에서 USER으로 변경되었습니다.'
       END,
       'INFO',
       t.v,
       t.v || ':' || ru.id || ':dummy-' || n
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
) t
CROSS JOIN LATERAL (
  SELECT CASE WHEN n % 5 < 2 THEN 1 + ((n * 13) % (:n_users / 10)) ELSE 1 + (n % :n_users) END AS idx
) rx
CROSS JOIN LATERAL (
  SELECT CASE WHEN 1 + ((n * 17) % :n_users) = rx.idx
              THEN 1 + ((n * 17 + 1) % :n_users)
              ELSE 1 + ((n * 17) % :n_users) END AS idx
) ax
JOIN users ru ON ru.id = pg_temp.duuid('d0000001', rx.idx)
JOIN users au ON au.id = pg_temp.duuid('d0000001', ax.idx)
JOIN playlists pl ON pl.id = pg_temp.duuid('d0000005', 1 + (n % :n_playlists))
JOIN contents ct ON ct.id = pg_temp.duuid('d0000002', 1 + (n % :n_contents))
CROSS JOIN LATERAL (
  SELECT pg_temp.dchild(GREATEST(ru.created_at, now() - interval '6 months'), now()) AS t
) ns;

-- 시청 세션: 콘텐츠별 멱법칙(인기 편중 유지, 최다 약 3,200명). 10%는 활성(exited_at NULL).
-- 활성 여부는 (c + k) % 10 기준: k만 쓰면 시청 수 10 미만인 꼬리 콘텐츠가 전부 비활성이 되어 비율이 낮아진다.
-- uk_watching_sessions_content_user: 유저 = 1 + ((c*11 + k*99991) % n_users) 로 콘텐츠 내 중복 없음.
-- 시간 리얼리즘: 활성 세션은 "지금 보는 중"이어야 하므로 joined_at 을 최근 4시간 내로,
-- 종료 세션은 콘텐츠 등록/유저 가입 이후 ~ 4시간 전 사이(시청 5~180분)로 둔다.
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
       ws.jt, ws.jt, NULL,
       ct.id,
       wu.id,
       ws.jt,
       CASE WHEN (pc.c + k) % 10 = 0 THEN NULL
            ELSE ws.jt + (5 + random() * 175) * interval '1 minute' END
FROM per_c pc
CROSS JOIN LATERAL generate_series(1, pc.cnt) AS k
JOIN contents ct ON ct.id = pg_temp.duuid('d0000002', pc.c)
JOIN users wu ON wu.id = pg_temp.duuid('d0000001', 1 + ((pc.c::bigint * 11 + k::bigint * 99991) % :n_users))
CROSS JOIN LATERAL (
  SELECT CASE WHEN (pc.c + k) % 10 = 0
              THEN GREATEST(ct.created_at, wu.created_at, now() - random() * interval '4 hours')
              ELSE pg_temp.dchild(GREATEST(ct.created_at, wu.created_at), now() - interval '4 hours') END AS jt
) ws;

COMMIT;
