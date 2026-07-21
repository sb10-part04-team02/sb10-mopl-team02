-- 한 날짜에 pages-per-run(25p x 20 = 500건)을 넘는 물량이 몰리면 커서 날짜만으로는 이어 읽을 수 없어
-- (discover는 lte만 걸고 page=1부터 다시 읽으므로) 남은 페이지가 버려졌다.
-- 다음 실행이 시작할 페이지를 함께 기록해 같은 날짜를 여러 실행에 걸쳐 이어 읽는다.
ALTER TABLE ingestion_backfill_cursor
    ADD COLUMN next_page INTEGER NOT NULL DEFAULT 1; -- 다음 실행의 시작 페이지. 날짜가 전진하면 1로 리셋
