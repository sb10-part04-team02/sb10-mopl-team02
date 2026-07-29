-- 더미 데이터 전체 삭제. 자식 -> 부모 순으로 테이블당 1회 PK range DELETE.
--
-- 임시 FK 인덱스를 먼저 만드는 이유:
-- 부모(users/contents/playlists/conversations) 삭제 시 FK CASCADE 트리거가 행마다 자식 테이블을
-- 조회하는데, 이 스키마의 자식 FK 인덱스는 대부분 partial(WHERE deleted_at IS NULL)이라 RI 트리거가
-- 쓸 수 없다. 인덱스 없이 지우면 (부모 행 수 x 자식 seq scan)으로 수 시간이 걸릴 수 있다.
-- (자식 행을 먼저 지워도 vacuum 전까지 dead page가 남아 seq scan 비용이 그대로다)
-- 임시 인덱스 생성은 전체 1~2회 스캔 비용이라 풀 규모에서도 수십 초 수준.
\ir _helpers.sql

BEGIN;

-- 1) RI 트리거용 임시 FK 인덱스 (기존에 non-partial 인덱스가 있는 컬럼은 제외:
--    direct_messages.conversation_id, notifications.receiver_id)
CREATE INDEX tmp_dummy_ix_reviews_author       ON reviews (author_id);
CREATE INDEX tmp_dummy_ix_reviews_content      ON reviews (content_id);
CREATE INDEX tmp_dummy_ix_tags_content         ON tags (content_id);
CREATE INDEX tmp_dummy_ix_pc_content           ON playlist_contents (content_id);
CREATE INDEX tmp_dummy_ix_pc_playlist          ON playlist_contents (playlist_id);
CREATE INDEX tmp_dummy_ix_ws_content           ON watching_sessions (content_id);
CREATE INDEX tmp_dummy_ix_ws_user              ON watching_sessions (user_id);
CREATE INDEX tmp_dummy_ix_subs_user            ON playlist_subscriptions (user_id);
CREATE INDEX tmp_dummy_ix_subs_playlist        ON playlist_subscriptions (playlist_id);
CREATE INDEX tmp_dummy_ix_playlists_owner      ON playlists (owner_id);
CREATE INDEX tmp_dummy_ix_follows_follower     ON follows (follower_id);
CREATE INDEX tmp_dummy_ix_follows_followee     ON follows (followee_id);
CREATE INDEX tmp_dummy_ix_cm_member            ON conversation_members (member_id);
CREATE INDEX tmp_dummy_ix_cm_conversation      ON conversation_members (conversation_id);
CREATE INDEX tmp_dummy_ix_dm_sender            ON direct_messages (sender_id);
CREATE INDEX tmp_dummy_ix_dm_receiver          ON direct_messages (receiver_id);
CREATE INDEX tmp_dummy_ix_social_user          ON social_accounts (user_id);

-- 2) 자식 -> 부모 순 삭제
DELETE FROM direct_messages        WHERE id BETWEEN pg_temp.duuid('d000000a', 0) AND 'd000000a-ffff-ffff-ffff-ffffffffffff';
DELETE FROM conversation_members   WHERE id BETWEEN pg_temp.duuid('d0000009', 0) AND 'd0000009-ffff-ffff-ffff-ffffffffffff';
DELETE FROM conversations          WHERE id BETWEEN pg_temp.duuid('d0000008', 0) AND 'd0000008-ffff-ffff-ffff-ffffffffffff';
DELETE FROM notifications          WHERE id BETWEEN pg_temp.duuid('d000000b', 0) AND 'd000000b-ffff-ffff-ffff-ffffffffffff';
DELETE FROM watching_sessions      WHERE id BETWEEN pg_temp.duuid('d000000c', 0) AND 'd000000c-ffff-ffff-ffff-ffffffffffff';
DELETE FROM playlist_subscriptions WHERE id BETWEEN pg_temp.duuid('d0000007', 0) AND 'd0000007-ffff-ffff-ffff-ffffffffffff';
DELETE FROM playlist_contents      WHERE id BETWEEN pg_temp.duuid('d0000006', 0) AND 'd0000006-ffff-ffff-ffff-ffffffffffff';
DELETE FROM playlists              WHERE id BETWEEN pg_temp.duuid('d0000005', 0) AND 'd0000005-ffff-ffff-ffff-ffffffffffff';
DELETE FROM follows                WHERE id BETWEEN pg_temp.duuid('d0000004', 0) AND 'd0000004-ffff-ffff-ffff-ffffffffffff';
DELETE FROM reviews                WHERE id BETWEEN pg_temp.duuid('d0000003', 0) AND 'd0000003-ffff-ffff-ffff-ffffffffffff';
DELETE FROM tags                   WHERE id BETWEEN pg_temp.duuid('d000000d', 0) AND 'd000000d-ffff-ffff-ffff-ffffffffffff';
DELETE FROM social_accounts        WHERE id BETWEEN pg_temp.duuid('d000000e', 0) AND 'd000000e-ffff-ffff-ffff-ffffffffffff';
DELETE FROM users                  WHERE id BETWEEN pg_temp.duuid('d0000001', 0) AND 'd0000001-ffff-ffff-ffff-ffffffffffff';
DELETE FROM contents               WHERE id BETWEEN pg_temp.duuid('d0000002', 0) AND 'd0000002-ffff-ffff-ffff-ffffffffffff';

-- 3) 임시 인덱스 제거
DROP INDEX tmp_dummy_ix_reviews_author, tmp_dummy_ix_reviews_content, tmp_dummy_ix_tags_content,
           tmp_dummy_ix_pc_content, tmp_dummy_ix_pc_playlist, tmp_dummy_ix_ws_content, tmp_dummy_ix_ws_user,
           tmp_dummy_ix_subs_user, tmp_dummy_ix_subs_playlist, tmp_dummy_ix_playlists_owner,
           tmp_dummy_ix_follows_follower, tmp_dummy_ix_follows_followee, tmp_dummy_ix_cm_member,
           tmp_dummy_ix_cm_conversation, tmp_dummy_ix_dm_sender, tmp_dummy_ix_dm_receiver, tmp_dummy_ix_social_user;

COMMIT;
