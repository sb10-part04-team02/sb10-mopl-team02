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
-- n_celebs: 팔로우 in-degree 멱법칙용 "셀럽" 유저 수 (유저 1 ~ n_celebs).
--   하한 241(소수)은 팔로워당 셀럽 엣지 수(최대 약 0.3 x 790)보다 커야 stride 중복이 없다.
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
       (200000 * :SCALE)::bigint  AS n_watch_target,
       GREATEST(241, (1000 * :SCALE)::int) AS n_celebs
\gset

-- 결정적 UUID: 테이블별 8-hex 접두사 + 순번.
-- 예: duuid('d0000001', 42) = 'd0000001-0000-4000-8000-00000000002a'
-- 유니크 페어는 서로소 stride(99991: 소수)로 뽑아 수학적으로 중복을 배제하고,
-- FK는 조인 없이 산술식으로 재계산하며, 정리(cleanup)는 PK range scan 1회로 끝낸다.
-- 기존 더미(11111111-, 22222222-, dummy_watcher_%, dummy_limit_%) 네임스페이스와 충돌하지 않는다.
CREATE OR REPLACE FUNCTION pg_temp.duuid(code text, n bigint) RETURNS uuid
LANGUAGE sql IMMUTABLE AS
$$ SELECT (code || '-0000-4000-8000-' || lpad(to_hex(n), 12, '0'))::uuid $$;

-- 시간대 리얼리즘: 날짜는 유지하고 시각만 저녁 피크(70%: 17~25시 삼각, 피크 21시) /
-- 낮(30%: 8~17시 균등)으로 재배치한다. 25시는 다음날 새벽 1시.
CREATE OR REPLACE FUNCTION pg_temp.dhour(t timestamptz) RETURNS timestamptz
LANGUAGE sql VOLATILE AS
$$ SELECT date_trunc('day', t)
        + CASE WHEN random() < 0.7
               THEN (17 + (random() + random()) * 4) * interval '1 hour'
               ELSE (8 + random() * 9) * interval '1 hour' END $$;

-- 자식 행 생성 시각: 부모 생성 이후 ~ upper_t 사이에서 최근일수록 밀도가 높게(random^0.6),
-- 시각은 dhour 로 재배치. GREATEST 가 바깥이라 "자식 >= 부모" 인과가 항상 보장된다.
CREATE OR REPLACE FUNCTION pg_temp.dchild(parent timestamptz, upper_t timestamptz) RETURNS timestamptz
LANGUAGE sql VOLATILE AS
$$ SELECT GREATEST(parent,
                   LEAST(upper_t,
                         pg_temp.dhour(parent + power(random(), 0.6) * (upper_t - parent)))) $$;
