-- 공용 헬퍼. 모든 더미 SQL 파일이 첫 줄에서 \ir 로 include 한다. (psql 10+ 필요)
-- SCALE: 1.0 = 풀 규모(유저 10만). 축소 실행: psql -v SCALE=0.01 -f ...
\set ON_ERROR_STOP on
\timing on
\if :{?SCALE}
\else
\set SCALE 1.0
\endif

SET synchronous_commit = off;
SET work_mem = '256MB';
SET maintenance_work_mem = '512MB';

-- 규모 파생 변수 (기준 규모 x SCALE)
SELECT (100000 * :SCALE)::int     AS n_users,
       (20000 * :SCALE)::int      AS n_contents,
       (30000 * :SCALE)::int      AS n_playlists,
       (50000 * :SCALE)::int      AS n_conversations,
       (20000 * :SCALE)::int      AS n_social_accounts,
       (500000 * :SCALE)::bigint  AS n_notifications,
       (2000000 * :SCALE)::bigint AS n_reviews_target,
       (500000 * :SCALE)::bigint  AS n_follows_target,
       (400000 * :SCALE)::bigint  AS n_subs_target,
       (1000000 * :SCALE)::bigint AS n_dm_target,
       (200000 * :SCALE)::bigint  AS n_watch_target
\gset

-- 결정적 UUID: 테이블별 8-hex 접두사 + 순번.
-- 예: duuid('d0000001', 42) = 'd0000001-0000-4000-8000-00000000002a'
-- 유니크 페어는 서로소 stride(99991: 소수)로 뽑아 수학적으로 중복을 배제하고,
-- FK는 조인 없이 산술식으로 재계산하며, 정리(cleanup)는 PK range scan 1회로 끝낸다.
-- 기존 더미(11111111-, 22222222-, dummy_watcher_%, dummy_limit_%) 네임스페이스와 충돌하지 않는다.
CREATE OR REPLACE FUNCTION pg_temp.duuid(code text, n bigint) RETURNS uuid
LANGUAGE sql IMMUTABLE AS
$$ SELECT (code || '-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid $$;
