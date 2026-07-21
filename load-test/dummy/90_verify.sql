-- 적재 검증 (읽기 전용). 적재 때와 같은 SCALE 로 실행해야 기대치가 맞는다.
--   psql -v SCALE=1.0 -f 90_verify.sql
-- 행 수는 생성식이 결정적이므로 기대치를 동일 수식으로 재계산해 정확히 비교한다.
\ir _helpers.sql

\echo ''
\echo '=== 1) 행 수: actual = expected 이면 ok ==='
SELECT tbl, actual, expected, (actual = expected) AS ok FROM (
  SELECT 'users'::text AS tbl,
         (SELECT count(*) FROM users WHERE id BETWEEN pg_temp.duuid('d0000001', 0) AND 'd0000001-ffff-ffff-ffff-ffffffffffff') AS actual,
         (:n_users + 1)::bigint AS expected
  UNION ALL SELECT 'contents',
         (SELECT count(*) FROM contents WHERE id BETWEEN pg_temp.duuid('d0000002', 0) AND 'd0000002-ffff-ffff-ffff-ffffffffffff'),
         :n_contents
  UNION ALL SELECT 'tags',
         (SELECT count(*) FROM tags WHERE id BETWEEN pg_temp.duuid('d000000d', 0) AND 'd000000d-ffff-ffff-ffff-ffffffffffff'),
         (SELECT sum(CASE WHEN c % 10 = 9 THEN 2 ELSE 2 + (c % 3) END)
          FROM generate_series(1, :n_contents) AS c)
  UNION ALL SELECT 'social_accounts',
         (SELECT count(*) FROM social_accounts WHERE id BETWEEN pg_temp.duuid('d000000e', 0) AND 'd000000e-ffff-ffff-ffff-ffffffffffff'),
         :n_social_accounts
  UNION ALL SELECT 'reviews',
         (SELECT count(*) FROM reviews WHERE id BETWEEN pg_temp.duuid('d0000003', 0) AND 'd0000003-ffff-ffff-ffff-ffffffffffff'),
         (WITH w AS (SELECT c, power(c::float, -0.7) AS wt FROM generate_series(1, :n_contents) AS c),
               tot AS (SELECT sum(wt) AS s FROM w)
          SELECT sum(LEAST(:n_users, GREATEST(1, round(:n_reviews_target * w.wt / tot.s)))::int) FROM w, tot)
  UNION ALL SELECT 'follows',
         (SELECT count(*) FROM follows WHERE id BETWEEN pg_temp.duuid('d0000004', 0) AND 'd0000004-ffff-ffff-ffff-ffffffffffff'),
         (WITH w AS (SELECT f, power(f::float, -0.5) AS wt FROM generate_series(1, :n_users) AS f),
               tot AS (SELECT sum(wt) AS s FROM w),
               per_f AS (SELECT w.f, LEAST(:n_users - 1, GREATEST(1, round(:n_follows_target * w.wt / tot.s)))::int AS cnt FROM w, tot)
          SELECT count(*) FROM per_f pf
          CROSS JOIN LATERAL generate_series(1, pf.cnt) AS k
          WHERE (CASE WHEN k = 1 OR k * 10 <= pf.cnt * 3
                      THEN 1 + ((pf.f + k::bigint * 99991) % :n_celebs)
                      ELSE :n_celebs + 1 + ((pf.f + k::bigint * 99991) % (:n_users - :n_celebs)) END) <> pf.f)
  UNION ALL SELECT 'playlists',
         (SELECT count(*) FROM playlists WHERE id BETWEEN pg_temp.duuid('d0000005', 0) AND 'd0000005-ffff-ffff-ffff-ffffffffffff'),
         :n_playlists
  UNION ALL SELECT 'playlist_contents',
         (SELECT count(*) FROM playlist_contents WHERE id BETWEEN pg_temp.duuid('d0000006', 0) AND 'd0000006-ffff-ffff-ffff-ffffffffffff'),
         (SELECT sum(5 + (p % 16)) FROM generate_series(1, :n_playlists) AS p)
  UNION ALL SELECT 'playlist_subscriptions',
         (SELECT count(*) FROM playlist_subscriptions WHERE id BETWEEN pg_temp.duuid('d0000007', 0) AND 'd0000007-ffff-ffff-ffff-ffffffffffff'),
         (WITH w AS (SELECT p, power(p::float, -0.7) AS wt FROM generate_series(1, :n_playlists) AS p),
               tot AS (SELECT sum(wt) AS s FROM w),
               per_p AS (SELECT w.p, LEAST(:n_users - 1, GREATEST(1, round(:n_subs_target * w.wt / tot.s)))::int AS cnt FROM w, tot)
          SELECT count(*) FROM per_p pp
          CROSS JOIN LATERAL generate_series(1, pp.cnt) AS k
          WHERE 1 + ((pp.p::bigint * 13 + k::bigint * 99991) % :n_users) <> 1 + ((pp.p * 31) % :n_users))
  UNION ALL SELECT 'conversations',
         (SELECT count(*) FROM conversations WHERE id BETWEEN pg_temp.duuid('d0000008', 0) AND 'd0000008-ffff-ffff-ffff-ffffffffffff'),
         :n_conversations
  UNION ALL SELECT 'conversation_members',
         (SELECT count(*) FROM conversation_members WHERE id BETWEEN pg_temp.duuid('d0000009', 0) AND 'd0000009-ffff-ffff-ffff-ffffffffffff'),
         :n_conversations * 2
  UNION ALL SELECT 'direct_messages',
         (SELECT count(*) FROM direct_messages WHERE id BETWEEN pg_temp.duuid('d000000a', 0) AND 'd000000a-ffff-ffff-ffff-ffffffffffff'),
         (WITH w AS (SELECT n, power(n::float, -0.7) AS wt FROM generate_series(1, :n_conversations) AS n),
               tot AS (SELECT sum(wt) AS s FROM w)
          SELECT sum(LEAST(20000, GREATEST(2, round(:n_dm_target * w.wt / tot.s)))::int) FROM w, tot)
  UNION ALL SELECT 'notifications',
         (SELECT count(*) FROM notifications WHERE id BETWEEN pg_temp.duuid('d000000b', 0) AND 'd000000b-ffff-ffff-ffff-ffffffffffff'),
         :n_notifications
  UNION ALL SELECT 'watching_sessions',
         (SELECT count(*) FROM watching_sessions WHERE id BETWEEN pg_temp.duuid('d000000c', 0) AND 'd000000c-ffff-ffff-ffff-ffffffffffff'),
         (WITH w AS (SELECT c, power(c::float, -0.7) AS wt FROM generate_series(1, :n_contents) AS c),
               tot AS (SELECT sum(wt) AS s FROM w)
          SELECT sum(LEAST(:n_users, GREATEST(1, round(:n_watch_target * w.wt / tot.s)))::int) FROM w, tot)
) t;

\echo ''
\echo '=== 2) 비정규화 집계 정합 (모두 0이어야 함) ==='
SELECT count(*) AS review_agg_mismatch
FROM contents c
LEFT JOIN (
  SELECT content_id, avg(rating) AS ar, count(*)::int AS rc
  FROM reviews WHERE deleted_at IS NULL GROUP BY content_id
) a ON a.content_id = c.id
WHERE c.id BETWEEN pg_temp.duuid('d0000002', 0) AND 'd0000002-ffff-ffff-ffff-ffffffffffff'
  AND (c.review_count <> COALESCE(a.rc, 0)
       OR abs(c.average_rating - COALESCE(a.ar, 0.0)) > 1e-6);

SELECT count(*) AS subscriber_count_mismatch
FROM playlists p
LEFT JOIN (
  SELECT playlist_id, count(*) AS cnt
  FROM playlist_subscriptions WHERE deleted_at IS NULL GROUP BY playlist_id
) s ON s.playlist_id = p.id
WHERE p.id BETWEEN pg_temp.duuid('d0000005', 0) AND 'd0000005-ffff-ffff-ffff-ffffffffffff'
  AND p.subscriber_count <> COALESCE(s.cnt, 0);

\echo ''
\echo '=== 3) 도메인 규칙 (모두 0이어야 함) ==='
SELECT count(*) AS self_follows
FROM follows
WHERE id BETWEEN pg_temp.duuid('d0000004', 0) AND 'd0000004-ffff-ffff-ffff-ffffffffffff'
  AND follower_id = followee_id;

SELECT count(*) AS duplicate_reviews
FROM (
  SELECT author_id, content_id
  FROM reviews
  WHERE deleted_at IS NULL
    AND id BETWEEN pg_temp.duuid('d0000003', 0) AND 'd0000003-ffff-ffff-ffff-ffffffffffff'
  GROUP BY author_id, content_id
  HAVING count(*) > 1
) d;

SELECT count(*) AS rating_out_of_range
FROM reviews
WHERE id BETWEEN pg_temp.duuid('d0000003', 0) AND 'd0000003-ffff-ffff-ffff-ffffffffffff'
  AND (rating < 0.0 OR rating > 5.0);

SELECT count(*) AS dm_member_conversation_mismatch
FROM direct_messages m
JOIN conversation_members s ON s.id = m.sender_id
JOIN conversation_members r ON r.id = m.receiver_id
WHERE m.id BETWEEN pg_temp.duuid('d000000a', 0) AND 'd000000a-ffff-ffff-ffff-ffffffffffff'
  AND (s.conversation_id <> m.conversation_id
       OR r.conversation_id <> m.conversation_id
       OR s.id = r.id);

SELECT count(*) AS watching_time_violation
FROM watching_sessions
WHERE id BETWEEN pg_temp.duuid('d000000c', 0) AND 'd000000c-ffff-ffff-ffff-ffffffffffff'
  AND exited_at IS NOT NULL AND exited_at < joined_at;

SELECT count(*) AS review_before_content_created
FROM reviews r
JOIN contents c ON c.id = r.content_id
WHERE r.id BETWEEN pg_temp.duuid('d0000003', 0) AND 'd0000003-ffff-ffff-ffff-ffffffffffff'
  AND r.created_at < c.created_at;

\echo ''
\echo '=== 4) 분포 확인 (육안) ==='
\echo '--- rating 히스토그램 (정수 1~5, 4~5 편중 J-curve) ---'
SELECT rating, count(*) AS cnt
FROM reviews
WHERE id BETWEEN pg_temp.duuid('d0000003', 0) AND 'd0000003-ffff-ffff-ffff-ffffffffffff'
GROUP BY rating ORDER BY rating;

\echo '--- 리뷰 수 상위 10 콘텐츠 (멱법칙 head) ---'
SELECT title, review_count, round(average_rating::numeric, 2) AS avg_rating
FROM contents
WHERE id BETWEEN pg_temp.duuid('d0000002', 0) AND 'd0000002-ffff-ffff-ffff-ffffffffffff'
ORDER BY review_count DESC LIMIT 10;

\echo '--- 구독자 수 상위 5 플레이리스트 ---'
SELECT title, subscriber_count
FROM playlists
WHERE id BETWEEN pg_temp.duuid('d0000005', 0) AND 'd0000005-ffff-ffff-ffff-ffffffffffff'
ORDER BY subscriber_count DESC LIMIT 5;

\echo '--- 활성 시청 세션 비율 (약 10% 기대) / 활성 세션은 전부 최근 4시간 내 참여여야 함 ---'
SELECT count(*) FILTER (WHERE exited_at IS NULL) AS active,
       count(*) AS total,
       round(100.0 * count(*) FILTER (WHERE exited_at IS NULL) / count(*), 1) AS active_pct,
       count(*) FILTER (WHERE exited_at IS NULL AND joined_at < now() - interval '5 hours') AS stale_active
FROM watching_sessions
WHERE id BETWEEN pg_temp.duuid('d000000c', 0) AND 'd000000c-ffff-ffff-ffff-ffffffffffff';

\echo '--- 최근 7일 활동량 (시간 분포가 현재까지 이어지는지: 모두 0보다 커야 자연스러움) ---'
SELECT (SELECT count(*) FROM users
        WHERE id BETWEEN pg_temp.duuid('d0000001', 0) AND 'd0000001-ffff-ffff-ffff-ffffffffffff'
          AND created_at > now() - interval '7 days') AS new_users_7d,
       (SELECT count(*) FROM reviews
        WHERE id BETWEEN pg_temp.duuid('d0000003', 0) AND 'd0000003-ffff-ffff-ffff-ffffffffffff'
          AND created_at > now() - interval '7 days') AS reviews_7d,
       (SELECT count(*) FROM direct_messages
        WHERE id BETWEEN pg_temp.duuid('d000000a', 0) AND 'd000000a-ffff-ffff-ffff-ffffffffffff'
          AND created_at > now() - interval '7 days') AS dms_7d,
       (SELECT count(*) FROM notifications
        WHERE id BETWEEN pg_temp.duuid('d000000b', 0) AND 'd000000b-ffff-ffff-ffff-ffffffffffff'
          AND created_at > now() - interval '7 days') AS notifications_7d;
