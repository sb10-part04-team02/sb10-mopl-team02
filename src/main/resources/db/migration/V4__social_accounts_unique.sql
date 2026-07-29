-- 동일한 소셜계정으로 여러 User에 연결 불가능
-- 유저는 동일한 소셜회원가입 불가능
CREATE UNIQUE INDEX uk_social_accounts_provider_provider_user_id
    ON social_accounts (provider, provider_user_id) WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX uk_social_accounts_user_provider
    ON social_accounts (user_id, provider) WHERE deleted_at IS NULL;
