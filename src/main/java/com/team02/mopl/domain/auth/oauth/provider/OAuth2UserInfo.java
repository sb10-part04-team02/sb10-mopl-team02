package com.team02.mopl.domain.auth.oauth.provider;

public record OAuth2UserInfo(
    OAuthType authType, String socialUserId, String name, String email, String profileImageUrl) {}
