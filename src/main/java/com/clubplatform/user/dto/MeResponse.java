package com.clubplatform.user.dto;

public record MeResponse(
        long id, String intraLogin, String displayName, String role, String profileImageUrl) {}
