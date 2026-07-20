-- follows(약 50만) + playlists(3만) + playlist_contents(약 37.5만) + playlist_subscriptions(약 40만)
-- + playlists.subscriber_count 집계 반영
\ir _helpers.sql

BEGIN;

-- 팔로우: 팔로워별 out-degree 멱법칙(지수 0.5, 최다 약 790명). 티어 1.
-- 팔로위 = 1 + ((f-1 + k*99991) % n_users): k*99991 % n_users != 0 이라 self-follow 불가능, k별 중복 불가능.
WITH w AS (
  SELECT f, power(f::float, -0.5) AS wt FROM generate_series(1, :n_users) AS f
), tot AS (
  SELECT sum(wt) AS s FROM w
), per_f AS (
  SELECT w.f, LEAST(:n_users - 1, GREATEST(1, round(:n_follows_target * w.wt / tot.s)))::int AS cnt
  FROM w, tot
)
INSERT INTO follows (id, created_at, deleted_at, follower_id, followee_id)
SELECT pg_temp.duuid('d0000004', (pf.f::bigint - 1) * :n_users + k),
       now() - interval '12 months' + random() * interval '6 months',
       NULL,
       pg_temp.duuid('d0000001', pf.f),
       pg_temp.duuid('d0000001', 1 + ((pf.f::bigint - 1 + k::bigint * 99991) % :n_users))
FROM per_f pf
CROSS JOIN LATERAL generate_series(1, pf.cnt) AS k;

-- 플레이리스트: 오너 = 유저 stride 매핑(일부 유저는 여러 개 소유). 티어 1.
INSERT INTO playlists (id, created_at, updated_at, deleted_at, owner_id, title, description, subscriber_count)
SELECT pg_temp.duuid('d0000005', p),
       ts.t, ts.t, NULL,
       pg_temp.duuid('d0000001', 1 + ((p * 31) % :n_users)),
       (ARRAY['주말에 보는','밤샘 각','눈물 쏙','심장 쫄깃','정주행용','입문자용','명작만 모은','숨은 보석','다시 보는','올해의'])[1 + (p % 10)]
         || ' ' || (ARRAY['영화 모음','드라마 리스트','스포츠 하이라이트','SF 컬렉션','스릴러 모음','로맨스 목록','애니 추천','다큐 셀렉션'])[1 + ((p / 10) % 8)]
         || ' #' || p,
       '취향대로 모아본 더미 플레이리스트입니다. (no.' || p || ')',
       0
FROM generate_series(1, :n_playlists) AS p
CROSS JOIN LATERAL (SELECT now() - interval '12 months' + random() * interval '6 months' AS t) ts;

-- 플레이리스트-콘텐츠: 플리당 5~20개, 콘텐츠 = 1 + ((p*7 + k*13337) % n_contents) (13337: 소수, 중복 없음). 티어 2.
INSERT INTO playlist_contents (id, created_at, deleted_at, content_id, playlist_id)
SELECT pg_temp.duuid('d0000006', (p::bigint - 1) * 20 + k),
       now() - interval '6 months' + random() * interval '179 days',
       NULL,
       pg_temp.duuid('d0000002', 1 + ((p * 7 + k * 13337) % :n_contents)),
       pg_temp.duuid('d0000005', p)
FROM generate_series(1, :n_playlists) AS p
CROSS JOIN LATERAL generate_series(1, 5 + (p % 16)) AS k;

-- 구독: 플리별 멱법칙(지수 0.7). 구독자 = 1 + ((p*13 + k*99991) % n_users), 오너와 같으면 스킵. 티어 2.
WITH w AS (
  SELECT p, power(p::float, -0.7) AS wt FROM generate_series(1, :n_playlists) AS p
), tot AS (
  SELECT sum(wt) AS s FROM w
), per_p AS (
  SELECT w.p, LEAST(:n_users - 1, GREATEST(1, round(:n_subs_target * w.wt / tot.s)))::int AS cnt
  FROM w, tot
)
INSERT INTO playlist_subscriptions (id, created_at, deleted_at, user_id, playlist_id)
SELECT pg_temp.duuid('d0000007', (pp.p::bigint - 1) * :n_users + k),
       now() - interval '6 months' + random() * interval '179 days',
       NULL,
       pg_temp.duuid('d0000001', 1 + ((pp.p::bigint * 13 + k::bigint * 99991) % :n_users)),
       pg_temp.duuid('d0000005', pp.p)
FROM per_p pp
CROSS JOIN LATERAL generate_series(1, pp.cnt) AS k
WHERE 1 + ((pp.p::bigint * 13 + k::bigint * 99991) % :n_users) <> 1 + ((pp.p * 31) % :n_users);

-- 비정규화 컬럼 정합: subscriber_count = 활성 구독 행 수 (구독 없는 플리는 DEFAULT 0 그대로 정합)
UPDATE playlists pl
SET subscriber_count = s.cnt
FROM (
  SELECT playlist_id, count(*) AS cnt
  FROM playlist_subscriptions
  WHERE deleted_at IS NULL
    AND id BETWEEN pg_temp.duuid('d0000007', 0) AND 'd0000007-ffff-ffff-ffff-ffffffffffff'
  GROUP BY playlist_id
) s
WHERE pl.id = s.playlist_id;

COMMIT;
