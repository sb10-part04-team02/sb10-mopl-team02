-- 한국어 정렬규칙 추가
CREATE COLLATION IF NOT EXISTS ko_icu (
        provider = 'icu',
        locale = 'ko-KR'
);

-- contents, conversations
-- watching_sessions
-- users
-- playlists, notifications, follows, reviews, tags, direct_messages, conversation_members, social_accounts
-- playlist_subscriptions, playlist_contents
--==================================================================================================

CREATE TABLE contents(
        id	                    UUID		        PRIMARY KEY,
        created_at	            TIMESTAMPTZ		    NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at	            TIMESTAMPTZ		    NULL,
        deleted_at              TIMESTAMPTZ         NULL,
        content_type	        VARCHAR(20)		    NOT NULL,
        title	                VARCHAR(100)		NOT NULL,
        description	            VARCHAR(255)		NOT NULL,
        thumbnail_url	        TEXT		        NOT NULL,
        average_rating	        DOUBLE PRECISION	NOT	NULL DEFAULT 0.0,
        review_count	        INT		            NOT NULL DEFAULT 0,

        CONSTRAINT chk_contents_content_type CHECK (content_type IN ('movie', 'tvSeries', 'sport'))
);

CREATE TABLE conversations(
        id	                    UUID		        PRIMARY KEY,
        created_at	            TIMESTAMPTZ		    NOT NULL DEFAULT CURRENT_TIMESTAMP,
        deleted_at              TIMESTAMPTZ         NULL
);

--==================================================================================================

CREATE TABLE watching_sessions(
        id	                    UUID		        PRIMARY KEY,
        created_at	            TIMESTAMPTZ		    NOT NULL DEFAULT CURRENT_TIMESTAMP,
        deleted_at              TIMESTAMPTZ         NULL,
        content_id	            UUID		        NOT NULL,

        CONSTRAINT fk_watching_sessions_contents FOREIGN KEY (content_id) REFERENCES contents (id) ON DELETE CASCADE
);

--==================================================================================================

CREATE TABLE users(
        id	                    UUID		        PRIMARY KEY,
        created_at	            TIMESTAMPTZ		    NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at	            TIMESTAMPTZ		    NULL,
        deleted_at              TIMESTAMPTZ         NULL,
        name	                VARCHAR(100)		COLLATE ko_icu NOT NULL,
        email	                VARCHAR(255)		COLLATE ko_icu NOT NULL,
        password	            VARCHAR(255)		NULL,
        profile_image_url	    TEXT		        NULL,
        role	                VARCHAR(10)	        NOT NULL DEFAULT 'USER',
        is_locked	            BOOLEAN	            NOT NULL DEFAULT FALSE,
        watching_session_id     UUID                NULL,

        CONSTRAINT fk_users_watching_sessions FOREIGN KEY (watching_session_id) REFERENCES watching_sessions (id) ON DELETE SET NULL,
        CONSTRAINT uk_users_email UNIQUE (email),
        CONSTRAINT chk_users_role CHECK (role IN ('USER', 'ADMIN'))
);

--==================================================================================================

CREATE TABLE playlists(
        id	                    UUID		        PRIMARY KEY,
        created_at	            TIMESTAMPTZ		    NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at	            TIMESTAMPTZ		    NULL,
        deleted_at              TIMESTAMPTZ         NULL,
        owner_id	            UUID		        NOT NULL,
        title	                VARCHAR(100)		NOT NULL,
        description	            VARCHAR(255)		NOT NULL,
        subscriber_count	    BIGINT              NOT NULL DEFAULT 0,

        CONSTRAINT fk_playlists_users FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE notifications(
        id	                    UUID		        PRIMARY KEY,
        created_at	            TIMESTAMPTZ		    NOT NULL DEFAULT CURRENT_TIMESTAMP,
        receiver_id	            UUID		        NOT NULL,
        title	                VARCHAR(100)	    NOT NULL,
        content	                VARCHAR(255)	    NOT NULL,
        level	                VARCHAR(10)	        NOT NULL DEFAULT 'INFO',
        notification_type       VARCHAR(20)         NOT NULL,
        is_read                 BOOLEAN             NOT NULL DEFAULT FALSE,
        read_at                 TIMESTAMPTZ         NULL,

        CONSTRAINT fk_notifications_users FOREIGN KEY (receiver_id) REFERENCES users (id) ON DELETE CASCADE,
        CONSTRAINT chk_notifications_level CHECK (level IN ('INFO', 'WARNING', 'ERROR')),
        CONSTRAINT chk_notifications_notification_type CHECK (notification_type IN ('ROLE_UPDATED', 'PLAYLIST_SUBSCRIBED', 'PLAYLIST_CONTENT_ADDED',
                                                                                    'FOLLOWING_USER_ACTIVITY', 'USER_FOLLOWED', 'DIRECT_MESSAGE_RECEIVED'))
);

CREATE TABLE follows(
        id	                    UUID		        PRIMARY KEY,
        created_at	            TIMESTAMPTZ		    NOT NULL DEFAULT CURRENT_TIMESTAMP,
        deleted_at              TIMESTAMPTZ         NULL,
        follower_id	            UUID		        NOT NULL,
        followee_id	            UUID		        NOT NULL,

        CONSTRAINT fk_follows_users_follower FOREIGN KEY (follower_id) REFERENCES users (id) ON DELETE CASCADE,
        CONSTRAINT fk_follows_users_followee FOREIGN KEY (followee_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE reviews(
        id	                    UUID		        PRIMARY KEY,
        created_at	            TIMESTAMPTZ		    NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at	            TIMESTAMPTZ		    NULL,
        deleted_at              TIMESTAMPTZ         NULL,
        author_id	            UUID		        NOT NULL,
        content_id	            UUID		        NOT NULL,
        text	                TEXT		        NOT NULL,
        rating	                DOUBLE PRECISION    NOT NULL DEFAULT 0.0,

        CONSTRAINT fk_reviews_users FOREIGN KEY (author_id) REFERENCES users (id) ON DELETE CASCADE,
        CONSTRAINT fk_reviews_contents FOREIGN KEY (content_id) REFERENCES contents (id) ON DELETE CASCADE,
        CONSTRAINT chk_reviews_rating CHECK (rating >= 0.0 AND rating <= 5.0)
);

CREATE TABLE tags(
        id	                    UUID		        PRIMARY KEY,
        created_at	            TIMESTAMPTZ		    NOT NULL DEFAULT CURRENT_TIMESTAMP,
        deleted_at              TIMESTAMPTZ         NULL,
        content_id	            UUID		        NOT NULL,
        name	                VARCHAR(20)		    NOT NULL,

        CONSTRAINT fk_tags_contents FOREIGN KEY (content_id) REFERENCES contents (id) ON DELETE CASCADE
);

CREATE TABLE direct_messages(
        id	                    UUID		        PRIMARY KEY,
        created_at	            TIMESTAMPTZ		    NOT NULL DEFAULT CURRENT_TIMESTAMP,
        deleted_at              TIMESTAMPTZ         NULL,
        conversation_id	        UUID		        NOT NULL,
        sender_id	            UUID		        NOT NULL,
        receiver_id	            UUID		        NOT NULL,
        content	                TEXT		        NOT NULL,

        CONSTRAINT fk_direct_messages_conversations FOREIGN KEY (conversation_id) REFERENCES conversations (id) ON DELETE CASCADE,
        CONSTRAINT fk_direct_messages_users_sender FOREIGN KEY (sender_id) REFERENCES users (id) ON DELETE CASCADE,
        CONSTRAINT fk_direct_messages_users_receiver FOREIGN KEY (receiver_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE conversation_members(
        id	                    UUID		        PRIMARY KEY,
        created_at	            TIMESTAMPTZ		    NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at	            TIMESTAMPTZ		    NULL,
        deleted_at              TIMESTAMPTZ         NULL,
        conversation_id	        UUID		        NOT NULL,
        member_id	            UUID		        NOT NULL,
        last_read_at	        TIMESTAMPTZ		    NOT NULL,

        CONSTRAINT fk_conversation_members_conversation FOREIGN KEY (conversation_id) REFERENCES conversations (id) ON DELETE CASCADE,
        CONSTRAINT fk_conversation_members_users FOREIGN KEY (member_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE social_accounts(
        id                      UUID                PRIMARY KEY,
        created_at	            TIMESTAMPTZ		    NOT NULL DEFAULT CURRENT_TIMESTAMP,
        deleted_at              TIMESTAMPTZ         NULL,
        user_id                 UUID                NOT NULL,
        provider                VARCHAR(20)         NOT NULL,
        provider_user_id        VARCHAR(100)        NOT NULL,

        CONSTRAINT fk_social_accounts_users FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
        CONSTRAINT chk_social_accounts_provider CHECK (provider IN ('GOOGLE', 'KAKAO'))
);

--==================================================================================================

CREATE TABLE playlist_subscriptions(
        id	                    UUID		        PRIMARY KEY,
        created_at	            TIMESTAMPTZ		    NOT NULL DEFAULT CURRENT_TIMESTAMP,
        deleted_at              TIMESTAMPTZ         NULL,
        user_id	                UUID		        NOT NULL,
        playlist_id	            UUID		        NOT NULL,

        CONSTRAINT fk_playlist_subscriptions_users FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
        CONSTRAINT fk_playlist_subscriptions_playlist FOREIGN KEY (playlist_id) REFERENCES playlists (id) ON DELETE CASCADE
);

CREATE TABLE playlist_contents(
        id	                    UUID		        PRIMARY KEY,
        created_at	            TIMESTAMPTZ		    NOT NULL DEFAULT CURRENT_TIMESTAMP,
        deleted_at              TIMESTAMPTZ         NULL,
        content_id	            UUID		        NOT NULL,
        playlist_id	            UUID		        NOT NULL,

        CONSTRAINT fk_playlist_contents_contents FOREIGN KEY (content_id) REFERENCES contents (id) ON DELETE CASCADE,
        CONSTRAINT fk_playlist_contents_playlists FOREIGN KEY (playlist_id) REFERENCES playlists (id) ON DELETE CASCADE
);


--==================================================================================================
-- Partial unique indexes (활성 행만 유니크: deleted_at IS NULL)
-- 인라인 UNIQUE 제약을 제거하고, 소프트 삭제된 행이 UK 자리를 점유하지 않도록 부분 유니크 인덱스로 대체
--==================================================================================================

CREATE UNIQUE INDEX uk_follows_follower_followee
        ON follows (follower_id, followee_id)
        WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX uk_reviews_user_content
        ON reviews (author_id, content_id)
        WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX uk_tags_content_name
        ON tags (content_id, name)
        WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX uk_conversation_members_conversation_member
        ON conversation_members (conversation_id, member_id)
        WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX uk_playlist_subscriptions_user_playlist
        ON playlist_subscriptions (user_id, playlist_id)
        WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX uk_playlist_contents_content_playlist
        ON playlist_contents (content_id, playlist_id)
        WHERE deleted_at IS NULL;
