-- follows(약 50만) + playlists(3만) + playlist_contents(약 37.5만) + playlist_subscriptions(약 40만)
-- + playlists.subscriber_count 집계 반영
\ir _helpers.sql

BEGIN;

-- 팔로우: 팔로워별 out-degree 멱법칙(지수 0.5, 최다 약 790명).
-- in-degree 리얼리즘: 첫 팔로우(k=1)와 엣지의 첫 30%(k*10 <= cnt*3)는 "셀럽"(유저 1~n_celebs)에게,
--   나머지는 그 뒤 구간에. 라이트 유저(팔로우 1~3개)도 셀럽부터 팔로우하는 실서비스 패턴을 재현해
--   상위 1% 유저가 전체 팔로우의 약 30%를 받는다. 셀럽 k-창은 연속 prefix {1..max(1, 0.3cnt)} 유지.
-- 중복 배제: 두 구간이 서로 겹치지 않고, stride 99991(소수)은 어떤 구간 크기와도 서로소이며
--   k 창(<=790)이 구간 크기(n_celebs>=241, n_users-n_celebs)보다 작아 (follower, followee) 중복이 없다.
-- self-follow 는 WHERE 로 제외(행 수 감소분은 90_verify 가 동일 식으로 재계산).
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
       pg_temp.dchild(GREATEST(fu.created_at, gu.created_at), now()),
       NULL,
       fu.id,
       gu.id
FROM per_f pf
CROSS JOIN LATERAL generate_series(1, pf.cnt) AS k
CROSS JOIN LATERAL (
  SELECT CASE WHEN k = 1 OR k * 10 <= pf.cnt * 3
              THEN 1 + ((pf.f + k::bigint * 99991) % :n_celebs)
              ELSE :n_celebs + 1 + ((pf.f + k::bigint * 99991) % (:n_users - :n_celebs))
         END AS idx
) fe
JOIN users fu ON fu.id = pg_temp.duuid('d0000001', pf.f)
JOIN users gu ON gu.id = pg_temp.duuid('d0000001', fe.idx)
WHERE fe.idx <> pf.f;

-- 플레이리스트: 오너 = 유저 stride 매핑(일부 유저는 여러 개 소유). 오너 가입 이후 시각.
INSERT INTO playlists (id, created_at, updated_at, deleted_at, owner_id, title, description, subscriber_count)
SELECT pg_temp.duuid('d0000005', p),
       ts.t, ts.t, NULL,
       ou.id,
       (ARRAY['주말에 보는','밤샘 각','눈물 쏙','심장 쫄깃','정주행용','입문자용','명작만 모은','숨은 보석','다시 보는','올해의','출퇴근길','비 오는 날'])[1 + (p % 12)]
         || ' ' || (ARRAY['영화 모음','드라마 리스트','스포츠 하이라이트','SF 컬렉션','스릴러 모음','로맨스 목록','애니 추천','다큐 셀렉션','인생작 모음','띵작 리스트'])[1 + ((p / 12) % 10)],
       (ARRAY['요즘 제일 자주 트는 리스트예요.','출퇴근길에 하나씩 보는 중입니다.','친구한테 추천받은 것만 모았어요.','주말 정주행용으로 딱입니다.',
              '보다가 운 작품만 모아뒀어요.','취향이 비슷하다면 구독 추천!','하나씩 지워가는 재미로 봅니다.','명장면만 다시 보고 싶을 때 꺼내요.',
              '입문자에게 추천하는 순서대로 담았어요.','밤에 보면 더 좋은 작품들입니다.','스포 없이 담백하게 골라봤어요.','언젠가 볼 것들 미리 저장해두는 곳.'])[1 + ((p * 7) % 12)],
       0
FROM generate_series(1, :n_playlists) AS p
JOIN users ou ON ou.id = pg_temp.duuid('d0000001', 1 + ((p * 31) % :n_users))
CROSS JOIN LATERAL (SELECT pg_temp.dchild(ou.created_at, now()) AS t) ts;

-- 플레이리스트-콘텐츠: 플리당 5~20개, 콘텐츠 = 1 + ((p*7 + k*13337) % n_contents) (13337: 소수, 중복 없음).
-- 시각은 플리 생성과 콘텐츠 등록 이후.
INSERT INTO playlist_contents (id, created_at, deleted_at, content_id, playlist_id)
SELECT pg_temp.duuid('d0000006', (p::bigint - 1) * 20 + k),
       pg_temp.dchild(GREATEST(pl.created_at, ct.created_at), now()),
       NULL,
       ct.id,
       pl.id
FROM generate_series(1, :n_playlists) AS p
CROSS JOIN LATERAL generate_series(1, 5 + (p % 16)) AS k
JOIN playlists pl ON pl.id = pg_temp.duuid('d0000005', p)
JOIN contents ct ON ct.id = pg_temp.duuid('d0000002', 1 + ((p * 7 + k * 13337) % :n_contents));

-- 구독: 플리별 멱법칙(지수 0.7). 구독자 = 1 + ((p*13 + k*99991) % n_users), 오너와 같으면 스킵.
-- 시각은 플리 생성과 구독자 가입 이후.
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
       pg_temp.dchild(GREATEST(pl.created_at, su.created_at), now()),
       NULL,
       su.id,
       pl.id
FROM per_p pp
CROSS JOIN LATERAL generate_series(1, pp.cnt) AS k
JOIN playlists pl ON pl.id = pg_temp.duuid('d0000005', pp.p)
JOIN users su ON su.id = pg_temp.duuid('d0000001', 1 + ((pp.p::bigint * 13 + k::bigint * 99991) % :n_users))
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
