-- discover 백필의 실행 간 진행 상태(커서) 저장.
-- 시간별 백필이 개봉일 내림차순으로 최신 -> 과거로 내려가는데, 다음에 어디까지 내려갈지를 기록
-- media_type 단위 1행(MOVIE/TV). 현재 TMDB만 백필하므로 source 컬럼 없이 media_type을 PK로 둔다.
CREATE TABLE ingestion_backfill_cursor
(
    media_type        VARCHAR(16) PRIMARY KEY,            -- 'MOVIE' | 'TV'
    cursor_date       DATE,                               -- 다음 실행의 개봉일 상한(lte). NULL이면 오늘(최신)부터 시작
    backfill_complete BOOLEAN     NOT NULL DEFAULT FALSE, -- floor-date까지 내려가 더 수집할 과거가 없음
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
