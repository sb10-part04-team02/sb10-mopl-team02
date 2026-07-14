-- 알림 Kafka 소비 멱등 처리를 위한 dedup_key 컬럼 및 UNIQUE 제약 추가
-- 동일 도메인 사건에 대해 재발행/재소비되어도 알림이 1건만 저장되도록 최종 방어선 역할

ALTER TABLE notifications
    ADD COLUMN dedup_key VARCHAR(255) NULL;

ALTER TABLE notifications
    ADD CONSTRAINT uq_notifications_receiver_dedup UNIQUE (receiver_id, dedup_key);
