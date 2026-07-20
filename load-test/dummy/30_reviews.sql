-- reviews(약 200만, 콘텐츠별 Zipf 편중) + contents.average_rating/review_count 집계 반영
-- uk_reviews_user_content(1인 1리뷰): 콘텐츠별 작성자를 서로소 stride(99991)로 뽑아 중복을 수학적으로 배제.
--   작성자 = 1 + ((c*7 + k*99991) % n_users), k = 1..cnt, cnt <= n_users
-- Zipf(지수 0.7) 정규화로 합계 ~ n_reviews_target, 최다 콘텐츠 약 3.2만 리뷰(< 유저 수).
\ir _helpers.sql

BEGIN;

WITH w AS (
  SELECT c, power(c::float, -0.7) AS wt FROM generate_series(1, :n_contents) AS c
), tot AS (
  SELECT sum(wt) AS s FROM w
), per_content AS (
  SELECT w.c, LEAST(:n_users, GREATEST(1, round(:n_reviews_target * w.wt / tot.s)))::int AS cnt
  FROM w, tot
)
INSERT INTO reviews (id, created_at, updated_at, deleted_at, author_id, content_id, text, rating)
SELECT pg_temp.duuid('d0000003', (pc.c::bigint - 1) * :n_users + k),
       ts.t, ts.t, NULL,
       pg_temp.duuid('d0000001', 1 + ((pc.c::bigint * 7 + k::bigint * 99991) % :n_users)),
       pg_temp.duuid('d0000002', pc.c),
       (ARRAY['연출이 정말 훌륭해요.','배우들의 연기가 압권입니다.','스토리 전개가 흡입력 있네요.','기대 이상으로 재미있었어요.',
              '중반부가 조금 늘어졌어요.','영상미가 뛰어납니다.','음악이 인상적이었어요.','반전이 충격적이네요.',
              '캐릭터들이 매력적입니다.','결말이 여운을 남겨요.','생각할 거리를 던져주는 작품입니다.','가볍게 보기 좋아요.',
              '기대했던 것보다는 아쉬웠어요.','몰입감이 대단합니다.','대사 하나하나가 좋았어요.','호흡이 빨라서 지루할 틈이 없어요.',
              '소재가 신선했습니다.'])[1 + (k % 17)]
         || ' ' ||
       (ARRAY['다시 봐도 좋을 것 같아요.','주변에 추천하고 싶습니다.','여운이 오래 남네요.','한 번쯤 볼만합니다.',
              '최고의 작품 중 하나예요.','호불호가 갈릴 것 같아요.','팬이라면 놓치지 마세요.','기대 없이 봤는데 만족했어요.',
              '시간 가는 줄 몰랐네요.','후속편이 기다려집니다.','평점이 이해되는 작품이에요.','정주행할 가치가 있어요.',
              '딱 제 취향이었습니다.'])[1 + ((pc.c + k) % 13)],
       -- 0.5 단위, 3.5 중심 삼각분포, [0.0, 5.0] 클램프
       round(LEAST(5.0, GREATEST(0.0, 3.5 + (random() + random() - 1.0) * 1.5)) * 2) / 2.0
FROM per_content pc
CROSS JOIN LATERAL generate_series(1, pc.cnt) AS k
CROSS JOIN LATERAL (SELECT now() - interval '6 months' + random() * interval '179 days' AS t) ts;

-- 비정규화 컬럼 정합: 더미 콘텐츠의 average_rating/review_count 를 실제 리뷰 집계와 일치시킨다.
-- (ContentRepository.refreshRatingAggregate 와 동일한 규칙: 활성 리뷰만 집계)
UPDATE contents c
SET average_rating = a.ar, review_count = a.rc
FROM (
  SELECT content_id, avg(rating) AS ar, count(*)::int AS rc
  FROM reviews
  WHERE deleted_at IS NULL
    AND content_id BETWEEN pg_temp.duuid('d0000002', 0) AND 'd0000002-ffff-ffff-ffff-ffffffffffff'
  GROUP BY content_id
) a
WHERE c.id = a.content_id;

COMMIT;
