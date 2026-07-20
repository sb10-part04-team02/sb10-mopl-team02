-- users(10만 + 관리자 1) + social_accounts(2만)
-- 모든 더미 유저는 공통 비밀번호 'password1!' 로 로그인 가능 (아래 BCrypt 해시).
-- 해시 재생성: htpasswd -bnBC 10 "" 'password1!' | cut -d: -f2
--   ($2y 형식이지만 Spring Security BCryptPasswordEncoder가 $2a/$2b/$2y 모두 검증 지원)
\ir _helpers.sql

\set DUMMY_PW_HASH '$2y$10$3pfr6zHu7I63b1HRkI6LOOG/0QTWnlricqJxu7h5vz5KyWq4Ufn1G'

BEGIN;

-- 가드: 더미 유저 네임스페이스(d0000001-*)가 이미 있으면 중단 (부분 재적재로 인한 정합성 붕괴 방지)
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM users
             WHERE id BETWEEN 'd0000001-0000-4000-8000-000000000000'
                          AND 'd0000001-ffff-ffff-ffff-ffffffffffff') THEN
    RAISE EXCEPTION '더미 데이터가 이미 존재합니다. 01_cleanup.sql 을 먼저 실행하세요.';
  END IF;
END $$;

-- 관리자 1명 (n=0)
INSERT INTO users (id, created_at, updated_at, deleted_at, name, email, password, profile_image_url, role, is_locked)
VALUES (pg_temp.duuid('d0000001', 0),
        now() - interval '24 months', now() - interval '24 months', NULL,
        '더미관리자', 'dummy_admin@mopl.test', :'DUMMY_PW_HASH',
        NULL, 'ADMIN', false);

-- 일반 유저 (티어 0: now()-24개월 ~ now()-12개월)
INSERT INTO users (id, created_at, updated_at, deleted_at, name, email, password, profile_image_url, role, is_locked)
SELECT pg_temp.duuid('d0000001', n),
       ts.t, ts.t, NULL,
       (ARRAY['김','이','박','최','정','강','조','윤','장','임'])[1 + (n % 10)]
         || (ARRAY['민준','서연','도윤','지우','하준','서준','하은','지호','수아','예준','시우','지아'])[1 + ((n / 10) % 12)]
         || lpad(n::text, 6, '0'),
       'dummy_bulk_' || lpad(n::text, 7, '0') || '@mopl.test',
       :'DUMMY_PW_HASH',
       'https://picsum.photos/seed/u' || n || '/200/200',
       'USER', false
FROM generate_series(1, :n_users) AS n
CROSS JOIN LATERAL (SELECT now() - interval '24 months' + random() * interval '365 days' AS t) ts;

-- 소셜 계정 (유저 1~n_social_accounts, GOOGLE/KAKAO 교대. 티어 1)
INSERT INTO social_accounts (id, created_at, deleted_at, user_id, provider, provider_user_id)
SELECT pg_temp.duuid('d000000e', n),
       now() - interval '12 months' + random() * interval '6 months',
       NULL,
       pg_temp.duuid('d0000001', n),
       CASE WHEN n % 2 = 0 THEN 'GOOGLE' ELSE 'KAKAO' END,
       'dummy-' || (CASE WHEN n % 2 = 0 THEN 'google' ELSE 'kakao' END) || '-' || n
FROM generate_series(1, :n_social_accounts) AS n;

COMMIT;
