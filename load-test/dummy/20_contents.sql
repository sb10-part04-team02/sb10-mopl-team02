-- contents(2만) + tags(콘텐츠당 2~5개, 약 5.8만)
-- MOVIE 60% / TV_SERIES 30% / SPORT 10%. 수집 콘텐츠 형태에 맞춰 source/external_id 쌍을 채운다.
-- (chk_contents_source_external_id_pair: 둘 다 있거나 둘 다 없거나 / uk_contents_source_external_id)
-- external_id 는 실수집 prefix 형식(movie:/tv:/event:)을 따르되 'dummy-' 를 끼워 실데이터와 충돌을 배제.
\ir _helpers.sql

BEGIN;

-- 콘텐츠: 배치 수집처럼 매일 03시 부근에 생성된 것으로 만들고, 24개월 전 ~ 최근까지 고르게 분포.
-- average_rating/review_count 는 30_reviews.sql 에서 집계 반영.
INSERT INTO contents (id, created_at, updated_at, deleted_at, content_type, title, description, thumbnail_url,
                      average_rating, review_count, source, external_id)
SELECT pg_temp.duuid('d0000002', c),
       ts.t, ts.t, NULL,
       ct.v,
       -- SPORT 는 "리그 N라운드 홈팀 vs 원정팀", MOVIE/TV 는 타입별 제목 풀 (일부는 부제/시즌 접미).
       CASE ct.v
         WHEN 'SPORT' THEN sp.league || ' ' || sp.round || '라운드 ' || sp.home || ' vs ' || sp.away
         WHEN 'MOVIE' THEN
           (ARRAY['운명의','마지막','사라진','조용한','위대한','미완의','한밤의','어느 겨울의','붉은','잊혀진','두 번째','낯선','오래된','첫 번째'])[1 + (c % 14)]
             || ' ' || (ARRAY['여행','약속','도시','계절','기록','초상','정원','파도','계단','불빛','편지','계약','증인','추격','왕국','귀환'])[1 + ((c / 14) % 16)]
             || CASE WHEN c % 3 = 0
                     THEN ': ' || (ARRAY['서울','새벽','침묵','귀향','각성','마지막 장','시작','재회','균열','종언'])[1 + ((c / 42) % 10)]
                     ELSE '' END
         ELSE
           (ARRAY['우리들의','슬기로운','어느 날','비밀의','붉은 달의','그해 여름의','낭만','미지의','청춘의','밤빛','골목길','회귀자의'])[1 + (c % 12)]
             || ' ' || (ARRAY['병원 24시','하숙집','로맨스','수사반','왕좌','오디션','편의점','연애사','법정','주방','캠퍼스','시그널'])[1 + ((c / 12) % 12)]
             || CASE WHEN c % 4 = 0 THEN ' 시즌 ' || (2 + c % 3) ELSE '' END
       END,
       -- SPORT 는 실수집기(SportsDbEventMapper)의 합성 형식 "{리그} {시즌} - {경기장} - {경기일}"을 따른다.
       CASE WHEN ct.v = 'SPORT'
            THEN sp.league || ' ' || to_char(ts.t, 'YYYY') || ' 시즌 - ' || sp.venue || ' - ' || to_char(ts.t, 'YYYY-MM-DD')
            ELSE (ARRAY['평범한 일상이 무너진 주인공이','오랜 비밀을 간직한 두 사람이','최고의 자리를 노리는 팀이','기억을 잃은 한 사람이',
                        '서로 다른 세계의 인물들이','사건의 진실을 쫓는 형사가','꿈을 포기하지 않은 청춘들이','모든 것을 잃은 한 가족이'])[1 + (c % 8)]
              || ' ' || (ARRAY['진실을 마주하며 벌어지는 이야기.','예상치 못한 선택 앞에 서는 이야기.','마지막 승부를 준비하는 이야기.','잃어버린 것을 되찾아가는 이야기.',
                               '함께 성장해가는 이야기.','서로에게 스며들며 변해가는 이야기.','거대한 음모에 맞서는 이야기.','두 번째 기회를 붙잡는 이야기.'])[1 + ((c / 8) % 8)]
       END,
       'https://picsum.photos/seed/c' || c || '/400/600',
       0.0, 0,
       CASE WHEN ct.v = 'SPORT' THEN 'SPORTS_DB' ELSE 'TMDB' END,
       CASE WHEN ct.v = 'SPORT' THEN 'event:dummy-' || c
            WHEN ct.v = 'MOVIE' THEN 'movie:dummy-' || c
            ELSE 'tv:dummy-' || c END
FROM generate_series(1, :n_contents) AS c
CROSS JOIN LATERAL (SELECT CASE WHEN c % 10 < 6 THEN 'MOVIE'
                                WHEN c % 10 < 9 THEN 'TV_SERIES'
                                ELSE 'SPORT' END AS v) ct
-- SPORT 제목/설명용: 스포츠 순번 s(=c/10)를 인덱스로 써서 4개 리그/8팀이 골고루 나오게 하고,
--                    종목별 8팀 풀(4x8)에서 홈/원정을 서로 다른 인덱스로 뽑는다. 경기장은 홈팀과 짝.
CROSS JOIN LATERAL (SELECT c / 10 AS s) ss
CROSS JOIN LATERAL (SELECT 1 + (ss.s % 4) AS li,
                           1 + (ss.s % 8) AS hi,
                           1 + ((ss.s % 8) + 1 + (ss.s / 8) % 7) % 8 AS ai) ix
CROSS JOIN LATERAL (
  SELECT (ARRAY['K리그1','KBO 리그','KBL','LCK'])[ix.li] AS league,
         1 + (ss.s / 4) % 38 AS round,
         tp.teams[ix.li][ix.hi] AS home,
         tp.teams[ix.li][ix.ai] AS away,
         tp.venues[ix.li][ix.hi] AS venue
  FROM (SELECT ARRAY[
          ARRAY['FC서울','전북 현대','울산 HD','포항 스틸러스','수원 삼성','대구 FC','인천 유나이티드','제주 유나이티드'],
          ARRAY['두산 베어스','LG 트윈스','KIA 타이거즈','삼성 라이온즈','롯데 자이언츠','SSG 랜더스','NC 다이노스','키움 히어로즈'],
          ARRAY['서울 SK','안양 정관장','창원 LG','원주 DB','수원 KT','대구 한국가스공사','고양 소노','부산 KCC'],
          ARRAY['T1','Gen.G','한화생명','KT 롤스터','디플러스 기아','광동 프릭스','농심 레드포스','BNK 피어엑스']
        ] AS teams,
        ARRAY[
          ARRAY['서울월드컵경기장','전주월드컵경기장','울산문수축구경기장','포항스틸야드','수원월드컵경기장','DGB대구은행파크','인천축구전용경기장','제주월드컵경기장'],
          ARRAY['잠실야구장','잠실야구장','광주기아챔피언스필드','대구삼성라이온즈파크','사직야구장','인천SSG랜더스필드','창원NC파크','고척스카이돔'],
          ARRAY['잠실학생체육관','안양정관장아레나','창원실내체육관','원주종합체육관','수원KT아레나','대구실내체육관','고양소노아레나','부산사직체육관'],
          ARRAY['롤파크','롤파크','롤파크','롤파크','롤파크','롤파크','롤파크','롤파크']
        ] AS venues) tp
) sp
-- seed 컬럼: c 를 참조해 LATERAL 을 상관 서브쿼리로 만든다 (비상관이면 random() 이 1회만 평가됨).
CROSS JOIN LATERAL (
  SELECT c AS seed,
         LEAST(now() - interval '6 hours',
               date_trunc('day', now() - random() * interval '24 months')
                 + interval '3 hours' + random() * interval '45 minutes') AS t
) ts;

-- 태그: 타입별 풀. MOVIE 는 TMDB 영화 장르(한국어), TV 는 TMDB TV 장르, SPORT 는 실수집기처럼 [종목, 리그] 2개.
-- MOVIE/TV 는 콘텐츠당 2~4개(2 + c%3), SPORT 는 2개. stride 7 은 풀 크기(18/16)와 서로소라 콘텐츠 내 중복 없음.
-- id 네임스페이스는 (c-1)*5 + k (k <= 4) 로 기존 폭 5 를 유지. created_at 은 수집기처럼 콘텐츠와 동일 시각.
INSERT INTO tags (id, created_at, deleted_at, content_id, name)
SELECT pg_temp.duuid('d000000d', (c::bigint - 1) * 5 + k),
       ct.created_at,
       NULL,
       ct.id,
       CASE WHEN c % 10 = 9 THEN
              (ARRAY[ARRAY['축구','K리그1'], ARRAY['야구','KBO 리그'], ARRAY['농구','KBL'], ARRAY['e스포츠','LCK']])[1 + ((c / 10) % 4)][k]
            WHEN c % 10 < 6 THEN
              (ARRAY['액션','모험','애니메이션','코미디','범죄','다큐멘터리','드라마','가족','판타지',
                     '역사','공포','음악','미스터리','로맨스','SF','스릴러','전쟁','서부'])[1 + ((c + k * 7) % 18)]
            ELSE
              (ARRAY['액션 & 어드벤처','애니메이션','코미디','범죄','다큐멘터리','드라마','가족','키즈',
                     '미스터리','뉴스','리얼리티','SF & 판타지','연속극','토크','전쟁 & 정치','서부'])[1 + ((c + k * 7) % 16)]
       END
FROM generate_series(1, :n_contents) AS c
CROSS JOIN LATERAL generate_series(1, CASE WHEN c % 10 = 9 THEN 2 ELSE 2 + (c % 3) END) AS k
JOIN contents ct ON ct.id = pg_temp.duuid('d0000002', c);

COMMIT;
