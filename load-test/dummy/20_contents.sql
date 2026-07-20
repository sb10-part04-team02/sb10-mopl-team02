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
       -- SPORT는 "리그 N라운드 홈팀 vs 원정팀"으로 실제 경기처럼, 그 외는 일반 작품 제목으로.
       CASE WHEN ct.v = 'SPORT' THEN sp.title
            ELSE (ARRAY['운명의','마지막','열두 번째','사라진','어느 날의','두 번째','조용한','위대한','미완의','한밤의'])[1 + (c % 10)]
                   || ' ' || (ARRAY['여행','약속','도시','계절','기록','초상','정원','파도','계단','불빛','수업','경기'])[1 + ((c / 10) % 12)]
                   || ' ' || c
       END,
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
-- SPORT 제목용: 스포츠 순번 s(=c/10)를 인덱스로 써서 4개 리그/8팀이 골고루 나오게 하고,
--               종목별 8팀 풀(4x8)에서 홈/원정을 서로 다른 인덱스로 뽑아 "vs" 매치명을 만든다.
CROSS JOIN LATERAL (
  SELECT (ARRAY['K리그1','KBO 리그','KBL','LCK'])[1 + (ss.s % 4)]
           || ' ' || (1 + (ss.s / 4) % 38) || '라운드 '
           || tp.teams[1 + (ss.s % 4)][1 + (ss.s % 8)]
           || ' vs '
           || tp.teams[1 + (ss.s % 4)][1 + ((ss.s % 8) + 1 + (ss.s / 8) % 7) % 8] AS title
  FROM (SELECT c / 10 AS s) ss,
       (SELECT ARRAY[
          ARRAY['FC서울','전북 현대','울산 HD','포항 스틸러스','수원 삼성','대구 FC','인천 유나이티드','제주 유나이티드'],
          ARRAY['두산 베어스','LG 트윈스','KIA 타이거즈','삼성 라이온즈','롯데 자이언츠','SSG 랜더스','NC 다이노스','키움 히어로즈'],
          ARRAY['서울 SK','안양 정관장','창원 LG','원주 DB','수원 KT','대구 한국가스공사','고양 소노','부산 KCC'],
          ARRAY['T1','Gen.G','한화생명','KT 롤스터','디플러스 기아','광동 프릭스','농심 레드포스','BNK 피어엑스']
        ] AS teams) tp
) sp
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
