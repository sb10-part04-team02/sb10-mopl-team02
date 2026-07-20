-- conversations(5만) + conversation_members(대화당 2명 = 10만) + direct_messages(약 100만)
-- direct_messages.sender_id/receiver_id 는 users 가 아니라 conversation_members.id 를 참조한다.
-- 멤버 id를 duuid('d0000009', 2n-1 / 2n)로 결정해두면 메시지가 조인 없이 산술식으로 참조 가능.
-- 시간 리얼리즘: 대화 시작은 두 멤버 가입 이후, 메시지는 대화 시작 ~ 대화별 활동 종료 시점
-- (수명의 25~100%, 결정적 해시)에 등간격으로 분포한다. 최근 종료 대화는 미읽음/SSE 시나리오 데이터가 된다.
\ir _helpers.sql

BEGIN;

-- 대화: 두 멤버(아래 conversation_members 와 동일한 매핑) 가입 이후 시각.
INSERT INTO conversations (id, created_at, deleted_at)
SELECT pg_temp.duuid('d0000008', n),
       pg_temp.dchild(GREATEST(ua.created_at, ub.created_at), now() - interval '1 hour'),
       NULL
FROM generate_series(1, :n_conversations) AS n
JOIN users ua ON ua.id = pg_temp.duuid('d0000001', 1 + ((n - 1) % :n_users))
JOIN users ub ON ub.id = pg_temp.duuid('d0000001', 1 + ((n - 1 + 1 + (n % (:n_users / 2))) % :n_users));

-- 대화 멤버: 대화 n = 유저 a(side 0), 유저 b(side 1). 오프셋 1 + n % (n_users/2) ∈ [1, n_users/2] 라 a != b 보장.
-- last_read_at: 절반은 모두 읽음(now 근처), 절반은 초반(수명의 ~30%)까지만 읽음(안 읽은 메시지 존재)
INSERT INTO conversation_members (id, created_at, updated_at, deleted_at, conversation_id, member_id, last_read_at)
SELECT pg_temp.duuid('d0000009', 2 * n - 1 + side),
       cv.created_at, cv.created_at, NULL,
       cv.id,
       CASE WHEN side = 0
            THEN pg_temp.duuid('d0000001', 1 + ((n - 1) % :n_users))
            ELSE pg_temp.duuid('d0000001', 1 + ((n - 1 + 1 + (n % (:n_users / 2))) % :n_users))
       END,
       CASE WHEN (n + side) % 2 = 0
            THEN now() - random() * interval '1 day'
            ELSE cv.created_at + random() * 0.3 * (now() - cv.created_at)
       END
FROM generate_series(1, :n_conversations) AS n
CROSS JOIN generate_series(0, 1) AS side
JOIN conversations cv ON cv.id = pg_temp.duuid('d0000008', n);

-- 메시지: 대화별 멱법칙(지수 0.7, 최소 2건, 최다 약 1.2만 건). 대화 내 시간 단조 증가, 홀짝 교대 송수신.
-- 대화별 활동 종료 시점(conv_end)은 수명의 25~100%를 결정적 해시((n*7919)%997)로 정해,
-- 일부 대화는 오래전에 잠들고 일부는 현재까지 활발한 형태를 만든다.
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
       cv.created_at + (ce.conv_end - cv.created_at) * (k::float / pc.cnt),
       NULL,
       cv.id,
       pg_temp.duuid('d0000009', 2 * pc.n - 1 + (k % 2)),
       pg_temp.duuid('d0000009', 2 * pc.n - (k % 2)),
       (ARRAY['이거 봤어?','오늘 저녁에 같이 볼까?','평점이 생각보다 높네','어제 그 경기 정말 대단했어','추천해줘서 고마워',
              '플리에 추가해둘게','스포 금지!','주말에 정주행 예정','리뷰 남겼어, 확인해봐','이번 화 결말 실화냐',
              '팝콘 준비 완료','시간 맞춰서 들어와','다음 편이 더 기대돼','그 배우 연기 미쳤다','하이라이트만 봐도 충분해'])[1 + ((pc.n + k) % 15)]
FROM per_conv pc
JOIN conversations cv ON cv.id = pg_temp.duuid('d0000008', pc.n)
CROSS JOIN LATERAL (
  SELECT cv.created_at + (0.25 + 0.75 * ((pc.n * 7919) % 997) / 997.0) * (now() - cv.created_at) AS conv_end
) ce
CROSS JOIN LATERAL generate_series(1, pc.cnt) AS k;

COMMIT;
