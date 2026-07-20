-- contents(2만) + tags(콘텐츠당 5개 = 10만)
-- MOVIE 60% / TV_SERIES 30% / SPORT 10%. 수집 콘텐츠 형태에 맞춰 source/external_id 쌍을 채운다.
-- (chk_contents_source_external_id_pair: 둘 다 있거나 둘 다 없거나 / uk_contents_source_external_id)
\ir _helpers.sql

BEGIN;

-- 콘텐츠 (티어 0: now()-24개월 ~ now()-12개월). average_rating/review_count는 30_reviews.sql에서 집계 반영.
INSERT INTO contents (id, created_at, updated_at, deleted_at, content_type, title, description, thumbnail_url,
                      average_rating, review_count, source, external_id)
SELECT pg_temp.duuid('d0000002', c),
       ts.t, ts.t, NULL,
       ct.v,
       (ARRAY['운명의','마지막','열두 번째','사라진','어느 날의','두 번째','조용한','위대한','미완의','한밤의'])[1 + (c % 10)]
         || ' ' || (ARRAY['여행','약속','도시','계절','기록','초상','정원','파도','계단','불빛','수업','경기'])[1 + ((c / 10) % 12)]
         || ' ' || c,
       (ARRAY['평범한 일상이 무너진 주인공이','오랜 비밀을 간직한 두 사람이','최고의 자리를 노리는 팀이','기억을 잃은 한 사람이','서로 다른 세계의 인물들이'])[1 + (c % 5)]
         || ' ' || (ARRAY['진실을 마주하며 벌어지는 이야기','예상치 못한 선택 앞에 서는 이야기','마지막 승부를 준비하는 이야기','잃어버린 것을 되찾아가는 이야기','함께 성장해가는 이야기'])[1 + ((c / 5) % 5)]
         || ' (no.' || c || ')',
       'https://picsum.photos/seed/c' || c || '/400/600',
       0.0, 0,
       CASE WHEN ct.v = 'SPORT' THEN 'SPORTS_DB' ELSE 'TMDB' END,
       CASE WHEN ct.v = 'SPORT' THEN 'dummy-sport-' || c
            WHEN ct.v = 'MOVIE' THEN 'dummy-movie-' || c
            ELSE 'dummy-tv-' || c END
FROM generate_series(1, :n_contents) AS c
CROSS JOIN LATERAL (SELECT CASE WHEN c % 10 < 6 THEN 'MOVIE'
                                WHEN c % 10 < 9 THEN 'TV_SERIES'
                                ELSE 'SPORT' END AS v) ct
CROSS JOIN LATERAL (SELECT now() - interval '24 months' + random() * interval '365 days' AS t) ts;

-- 태그 (콘텐츠당 5개, 풀 30개에서 stride 7로 선택 -> 콘텐츠 내 중복 없음. 티어 1)
INSERT INTO tags (id, created_at, deleted_at, content_id, name)
SELECT pg_temp.duuid('d000000d', (c::bigint - 1) * 5 + k),
       now() - interval '12 months' + random() * interval '6 months',
       NULL,
       pg_temp.duuid('d0000002', c),
       (ARRAY['드라마','액션','코미디','스릴러','로맨스','SF','판타지','공포','다큐','애니메이션',
              '범죄','미스터리','모험','가족','전쟁','역사','음악','스포츠','축구','야구',
              '농구','e스포츠','청춘','정치','법정','의학','시대극','느와르','힐링','서바이벌'])[1 + ((c + k * 7) % 30)]
FROM generate_series(1, :n_contents) AS c
CROSS JOIN generate_series(1, 5) AS k;

COMMIT;
