-- 한국어 정렬규칙 추가
CREATE COLLATION IF NOT EXISTS ko_icu (
    provider = 'icu',
    locale = 'ko-KR'
);

-- users, contents, conversations
-- playlists, watching_sessions
-- notifications, follows, reviews, tags, direct_messages, conversation_members
-- playlist_subscriptions, playlist_contents, watching_session_members
--==================================================================================================

CREATE TABLE users(
    id                      UUID		        PRIMARY KEY,
    created_at              TIMESTAMPTZ         NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at	            TIMESTAMPTZ		    NULL,
    deleted_at              TIMESTAMPTZ         NULL,
    login_type              VARCHAR(20)         NOT NULL DEFAULT 'LOCAL',
    name	                VARCHAR(100)		COLLATE ko_icu NOT NULL,
    email	                VARCHAR(255)		COLLATE ko_icu NOT NULL,
    password	            VARCHAR(255)		NULL,
    profile_image_url	    TEXT		        NULL,
    role	                VARCHAR(10)	        NOT NULL DEFAULT 'USER',
    is_locked	            BOOLEAN	            NOT NULL DEFAULT FALSE,

    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT chk_users_login_type CHECK (login_type IN ('LOCAL', 'GOOGLE', 'KAKAO')),
    CONSTRAINT chk_users_role CHECK (role IN ('USER', 'ADMIN'))
);

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

CREATE TABLE watching_sessions(
    id	                    UUID		        PRIMARY KEY,
    created_at	            TIMESTAMPTZ		    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at              TIMESTAMPTZ         NULL,
    content_id	            UUID		        NOT NULL,

    CONSTRAINT fk_watching_sessions_contents FOREIGN KEY (content_id) REFERENCES contents (id) ON DELETE CASCADE
);

--==================================================================================================

CREATE TABLE notifications(
    id	                    UUID		        PRIMARY KEY,
    created_at	            TIMESTAMPTZ		    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    receiver_id	            UUID		        NOT NULL,
    title	                VARCHAR(100)	    NOT NULL,
    content	                VARCHAR(255)	    NOT NULL,
    level	                VARCHAR(10)	        NOT NULL DEFAULT 'INFO',
    is_read                 BOOLEAN             NOT NULL DEFAULT FALSE,
    read_at                 TIMESTAMPTZ         NULL,

    CONSTRAINT fk_notifications_users FOREIGN KEY (receiver_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_notifications_level CHECK (level IN ('INFO', 'WARNING', 'ERROR'))
);

CREATE TABLE follows(
    id	                    UUID		        PRIMARY KEY,
    created_at	            TIMESTAMPTZ		    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at              TIMESTAMPTZ         NULL,
    follower_id	            UUID		        NOT NULL,
    followee_id	            UUID		        NOT NULL,

    CONSTRAINT fk_follows_users_follower FOREIGN KEY (follower_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_follows_users_followee FOREIGN KEY (followee_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_follows_follower_followee UNIQUE (follower_id, followee_id)
);

CREATE TABLE reviews(
    id	                    UUID		        PRIMARY KEY,
    created_at	            TIMESTAMPTZ		    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at	            TIMESTAMPTZ		    NULL,
    deleted_at              TIMESTAMPTZ         NULL,
    user_id	                UUID		        NOT NULL,
    content_id	            UUID		        NOT NULL,
    text	                TEXT		        NOT NULL,
    rating	                DOUBLE PRECISION    NOT NULL DEFAULT 0.0,

    CONSTRAINT fk_reviews_users FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_reviews_contents FOREIGN KEY (content_id) REFERENCES contents (id) ON DELETE CASCADE,
    CONSTRAINT uk_reviews_user_content UNIQUE (user_id, content_id),
    CONSTRAINT chk_reviews_rating CHECK (rating >= 0.0 AND rating <= 5.0)
);

CREATE TABLE tags(
    id	                    UUID		        PRIMARY KEY,
    created_at	            TIMESTAMPTZ		    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at              TIMESTAMPTZ         NULL,
    content_id	            UUID		        NOT NULL,
    name	                VARCHAR(20)		    NOT NULL,

    CONSTRAINT fk_tags_contents FOREIGN KEY (content_id) REFERENCES contents (id) ON DELETE CASCADE,
    CONSTRAINT uk_tags_content_name UNIQUE (content_id, name)
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
    CONSTRAINT fk_conversation_members_users FOREIGN KEY (member_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_conversation_members_conversation_member UNIQUE (conversation_id, member_id)
);

--==================================================================================================

CREATE TABLE playlist_subscriptions(
    id	                    UUID		        PRIMARY KEY,
    created_at	            TIMESTAMPTZ		    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at              TIMESTAMPTZ         NULL,
    user_id	                UUID		        NOT NULL,
    playlist_id	            UUID		        NOT NULL,

    CONSTRAINT fk_playlist_subscriptions_users FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_playlist_subscriptions_playlist FOREIGN KEY (playlist_id) REFERENCES playlists (id) ON DELETE CASCADE,
    CONSTRAINT uk_playlist_subscriptions_user_playlist UNIQUE (user_id, playlist_id)
);

CREATE TABLE playlist_contents(
    id	                    UUID		        PRIMARY KEY,
    created_at	            TIMESTAMPTZ		    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at              TIMESTAMPTZ         NULL,
    content_id	            UUID		        NOT NULL,
    playlist_id	            UUID		        NOT NULL,

    CONSTRAINT fk_playlist_contents_contents FOREIGN KEY (content_id) REFERENCES contents (id) ON DELETE CASCADE,
    CONSTRAINT fk_playlist_contents_playlists FOREIGN KEY (playlist_id) REFERENCES playlists (id) ON DELETE CASCADE,
    CONSTRAINT uk_playlist_contents_content_playlist UNIQUE (content_id, playlist_id)
);

CREATE TABLE watching_session_members(
    id                      UUID		        PRIMARY KEY,
    created_at              TIMESTAMPTZ		    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at              TIMESTAMPTZ         NULL,
    watching_session_id     UUID		        NOT NULL,
    member_id               UUID		        NOT NULL,

    CONSTRAINT fk_watching_session_members_watching_session FOREIGN KEY (watching_session_id) REFERENCES watching_sessions (id) ON DELETE CASCADE,
    CONSTRAINT fk_watching_session_members_users FOREIGN KEY (member_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_watching_session_members_watching_session_member UNIQUE (watching_session_id, member_id)
);
