-- reviews(약 200만, 콘텐츠별 Zipf 편중) + contents.average_rating/review_count 집계 반영
-- uk_reviews_user_content(1인 1리뷰): 콘텐츠별 작성자를 서로소 stride(99991)로 뽑아 중복을 수학적으로 배제.
--   작성자 = 1 + ((c*7 + k*99991) % n_users), k = 1..cnt, cnt <= n_users
-- Zipf(지수 0.7) 정규화로 합계 ~ n_reviews_target, 최다 콘텐츠 약 3.2만 리뷰(< 유저 수).
--
-- 평점 리얼리즘:
--   FE 는 정수 별점 1~5 만 입력하므로 rating 은 정수. 콘텐츠별 품질 계층(명작 20% / 평작 50% /
--   범작 20% / 졸작 10%)을 결정적 해시((c*37)%20)로 부여하고, 그 기준값에 삼각 노이즈를 더해
--   전체적으로는 4~5 편중(J-curve), 콘텐츠별 평균은 약 2점대~4점대로 갈리게 한다(rate 정렬 변별력).
--   리뷰 텍스트는 평점 구간(4~5 긍정 / 3 중립 / 1~2 부정)과 감성이 일치하는 풀에서 뽑는다.
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
       au.id,
       ct.id,
       CASE WHEN rt.r >= 4 THEN
              (ARRAY['연출이 정말 훌륭해요.','배우들의 연기가 압권입니다.','스토리 전개가 흡입력 있네요.','기대 이상으로 재미있었어요.',
                     '영상미가 뛰어납니다.','음악이 인상적이었어요.','반전이 충격적이네요.','캐릭터들이 매력적입니다.',
                     '몰입감이 대단합니다.','대사 하나하나가 좋았어요.'])[1 + (k % 10)]
                || ' ' || (ARRAY['다시 봐도 좋을 것 같아요.','주변에 추천하고 싶습니다.','여운이 오래 남네요.','최고의 작품 중 하나예요.',
                                 '팬이라면 놓치지 마세요.','시간 가는 줄 몰랐네요.','후속편이 기다려집니다.','정주행할 가치가 있어요.'])[1 + ((pc.c + k) % 8)]
            WHEN rt.r = 3 THEN
              (ARRAY['전체적으로 무난했어요.','중반부가 조금 늘어졌어요.','소재는 신선했습니다.','기대만큼은 아니었지만 볼만해요.',
                     '호흡이 느린 편이에요.','배우들 연기는 좋았습니다.','설정은 흥미로웠어요.','평이 갈릴 만한 작품이네요.'])[1 + (k % 8)]
                || ' ' || (ARRAY['한 번쯤 볼만합니다.','호불호가 갈릴 것 같아요.','가볍게 보기 좋아요.','기대 없이 보면 괜찮습니다.',
                                 '킬링타임용으로는 충분해요.','취향 타는 작품입니다.','후반부는 나름 괜찮았어요.'])[1 + ((pc.c + k) % 7)]
            ELSE
              (ARRAY['전개가 너무 뻔했어요.','개연성이 아쉽습니다.','중반부터 집중이 안 되네요.','캐릭터에 몰입이 안 됐어요.',
                     '편집이 산만하게 느껴졌습니다.','기대했던 것보다 많이 아쉬웠어요.','대사가 오글거려서 힘들었어요.','결말이 허무합니다.'])[1 + (k % 8)]
                || ' ' || (ARRAY['추천하기는 어렵네요.','제 취향은 아니었습니다.','평점이 아까워요.','끝까지 보기 힘들었어요.',
                                 '시간이 아까웠습니다.','왜 평점이 높은지 모르겠어요.','다시 볼 일은 없을 것 같아요.'])[1 + ((pc.c + k) % 7)]
       END,
       rt.r
FROM per_content pc
CROSS JOIN LATERAL generate_series(1, pc.cnt) AS k
JOIN contents ct ON ct.id = pg_temp.duuid('d0000002', pc.c)
JOIN users au ON au.id = pg_temp.duuid('d0000001', 1 + ((pc.c::bigint * 7 + k::bigint * 99991) % :n_users))
-- seed 컬럼: k 까지 참조해야 리뷰(행)마다 노이즈가 새로 뽑힌다.
-- (pc.c 만 참조하면 LATERAL 이 k 조인 앞에서 콘텐츠당 1회만 평가되어 전 리뷰가 같은 평점이 된다)
CROSS JOIN LATERAL (
  SELECT k AS seed,
         LEAST(5, GREATEST(1, round(
           CASE WHEN (pc.c * 37) % 20 < 4  THEN 4.5   -- 명작 20%
                WHEN (pc.c * 37) % 20 < 14 THEN 3.9   -- 평작 50%
                WHEN (pc.c * 37) % 20 < 18 THEN 3.1   -- 범작 20%
                ELSE 2.2 END                          -- 졸작 10%
           + (random() + random() - 1.0) * 1.6)))::int AS r
) rt
CROSS JOIN LATERAL (SELECT pg_temp.dchild(GREATEST(ct.created_at, au.created_at), now()) AS t) ts;

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
