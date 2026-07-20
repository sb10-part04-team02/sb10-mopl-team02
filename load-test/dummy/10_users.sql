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

-- 일반 유저: 가입 시각은 24개월 전 ~ 현재, 최근일수록 밀도가 높은 성장 곡선(random^1.5).
-- 이름은 실명형(성+이름, 약 86%)과 닉네임형(약 14%)을 섞는다. name 은 유니크 제약이 없어 중복 허용.
INSERT INTO users (id, created_at, updated_at, deleted_at, name, email, password, profile_image_url, role, is_locked)
SELECT pg_temp.duuid('d0000001', n),
       ts.t, ts.t, NULL,
       CASE WHEN n % 7 <> 0
            THEN (ARRAY['김','이','박','최','정','강','조','윤','장','임','한','오','서','신','권','황','안','송','전','홍'])[1 + (n % 20)]
                   || (ARRAY['민준','서연','도윤','지우','하준','서준','하은','지호','수아','예준','시우','지아',
                             '은우','채원','유준','다은','건우','소율','현우','지민','우진','서현','준서','예은',
                             '도현','시은','지환','하린','승우','유나','정우','가은','민재','윤서','태윤','서윤',
                             '지훈','나윤','준혁','예린'])[1 + ((n / 20 * 11) % 40)]
            ELSE (ARRAY['무비홀릭','정주행장인','팝콘성애자','밤샘시청러','시네필','드라마덕후','스포츠광','하이라이트봇',
                        '리뷰요정','별점자판기','소파감자','주말집돌이','극장죽돌이','OST수집가','스포주의보','결말맛집',
                        '자막없이본다','몰아보기달인','채널고정','본방사수','눈물버튼','킬링타임러','명대사수집가','N차관람러',
                        '쿠키영상지킴이','롤드컵직관러','야구장단골','축덕후','이불속관객','새벽두시감성','플리장인','알고리즘노예',
                        '취향존중','숨은명작발굴단','정속재생거부','엔딩크레딧까지'])[1 + ((n / 7 * 5) % 36)]
       END,
       'dummy_bulk_' || lpad(n::text, 7, '0') || '@mopl.test',
       :'DUMMY_PW_HASH',
       'https://picsum.photos/seed/u' || n || '/200/200',
       'USER', false
FROM generate_series(1, :n_users) AS n
-- seed 컬럼: n 을 참조해 LATERAL 을 상관 서브쿼리로 만든다.
-- (비상관 LATERAL 의 random() 은 PG가 1회만 평가해 전 행이 같은 값을 갖게 된다)
CROSS JOIN LATERAL (
  SELECT n AS seed,
         LEAST(now() - interval '1 hour',
               pg_temp.dhour(now() - interval '24 months' * power(random(), 1.5))) AS t
) ts;

-- 소셜 계정 (유저 1~n_social_accounts, GOOGLE/KAKAO 교대. 해당 유저 가입 이후 시각)
INSERT INTO social_accounts (id, created_at, deleted_at, user_id, provider, provider_user_id)
SELECT pg_temp.duuid('d000000e', n),
       pg_temp.dchild(u.created_at, now()),
       NULL,
       u.id,
       CASE WHEN n % 2 = 0 THEN 'GOOGLE' ELSE 'KAKAO' END,
       'dummy-' || (CASE WHEN n % 2 = 0 THEN 'google' ELSE 'kakao' END) || '-' || n
FROM generate_series(1, :n_social_accounts) AS n
JOIN users u ON u.id = pg_temp.duuid('d0000001', n);

COMMIT;
