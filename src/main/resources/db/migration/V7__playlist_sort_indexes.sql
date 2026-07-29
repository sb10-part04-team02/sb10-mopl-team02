--==================================================================================================
-- 플레이리스트 목록 커서 조회용 복합 인덱스 (활성 행만: deleted_at IS NULL 부분 인덱스)
-- 정렬 기준(updatedAt / subscribeCount)별 (정렬키, id) 복합 인덱스로 Seq Scan + top-N heapsort 를 제거한다.
-- ASC·DESC 양방향 모두 동일 컬럼 순서라 B-Tree 하나로 커버된다 (역방향 스캔).
--==================================================================================================
CREATE INDEX ix_playlists_updated_id
    ON playlists (updated_at, id) WHERE deleted_at IS NULL;

CREATE INDEX ix_playlists_subscriber_id
    ON playlists (subscriber_count, id) WHERE deleted_at IS NULL;
