-- conversations(5만) + conversation_members(대화당 2명 = 10만) + direct_messages(약 100만)
-- direct_messages.sender_id/receiver_id 는 users 가 아니라 conversation_members.id 를 참조한다.
-- 멤버 id를 duuid('d0000009', 2n-1 / 2n)로 결정해두면 메시지가 조인 없이 산술식으로 참조 가능.
-- 대화 created_at 을 n의 결정적 함수로 만들어(랜덤 없음) 메시지가 같은 식으로 재계산한다.
-- (now()는 같은 트랜잭션 안에서 상수이므로 두 문장의 값이 일치한다)
\ir _helpers.sql

BEGIN;

-- 대화 (티어 1: now()-12개월 ~ now()-6개월, 결정적)
INSERT INTO conversations (id, created_at, deleted_at)
SELECT pg_temp.duuid('d0000008', n),
       now() - interval '12 months' + (n % 180) * interval '1 day',
       NULL
FROM generate_series(1, :n_conversations) AS n;

-- 대화 멤버: 대화 n = 유저 a(side 0), 유저 b(side 1). 오프셋 1 + n % (n_users/2) ∈ [1, n_users/2] 라 a != b 보장.
-- last_read_at: 절반은 모두 읽음(now 근처), 절반은 초반까지만 읽음(안 읽은 메시지 존재)
INSERT INTO conversation_members (id, created_at, updated_at, deleted_at, conversation_id, member_id, last_read_at)
SELECT pg_temp.duuid('d0000009', 2 * n - 1 + side),
       now() - interval '12 months' + (n % 180) * interval '1 day' + interval '1 minute',
       now() - interval '12 months' + (n % 180) * interval '1 day' + interval '1 minute',
       NULL,
       pg_temp.duuid('d0000008', n),
       CASE WHEN side = 0
            THEN pg_temp.duuid('d0000001', 1 + ((n - 1) % :n_users))
            ELSE pg_temp.duuid('d0000001', 1 + ((n - 1 + 1 + (n % (:n_users / 2))) % :n_users))
       END,
       CASE WHEN (n + side) % 2 = 0
            THEN now() - random() * interval '1 day'
            ELSE now() - interval '12 months' + (n % 180) * interval '1 day' + interval '1 minute'
                 + random() * interval '5 days'
       END
FROM generate_series(1, :n_conversations) AS n
CROSS JOIN generate_series(0, 1) AS side;

-- 메시지: 대화별 멱법칙(지수 0.7, 최소 2건, 최다 약 1.2만 건). 대화 내 시간 단조 증가(2분 간격), 홀짝 교대 송수신.
WITH w AS (
  SELECT n, power(n::float, -0.7) AS wt FROM generate_series(1, :n_conversations) AS n
), tot AS (
  SELECT sum(wt) AS s FROM w
), per_conv AS (
  SELECT w.n, LEAST(20000, GREATEST(2, round(:n_dm_target * w.wt / tot.s)))::int AS cnt
  FROM w, tot
)
INSERT INTO direct_messages (id, created_at, deleted_at, conversation_id, sender_id, receiver_id, content)
SELECT pg_temp.duuid('d000000a', (pc.n::bigint - 1) * 20000 + k),
       now() - interval '12 months' + (pc.n % 180) * interval '1 day' + interval '1 minute' + k * interval '2 minutes',
       NULL,
       pg_temp.duuid('d0000008', pc.n),
       pg_temp.duuid('d0000009', 2 * pc.n - 1 + (k % 2)),
       pg_temp.duuid('d0000009', 2 * pc.n - (k % 2)),
       (ARRAY['이거 봤어?','오늘 저녁에 같이 볼까?','평점이 생각보다 높네','어제 그 경기 정말 대단했어','추천해줘서 고마워',
              '플리에 추가해둘게','스포 금지!','주말에 정주행 예정','리뷰 남겼어, 확인해봐','이번 화 결말 실화냐',
              '팝콘 준비 완료','시간 맞춰서 들어와','다음 편이 더 기대돼','그 배우 연기 미쳤다','하이라이트만 봐도 충분해'])[1 + ((pc.n + k) % 15)]
FROM per_conv pc
CROSS JOIN LATERAL generate_series(1, pc.cnt) AS k;

COMMIT;
