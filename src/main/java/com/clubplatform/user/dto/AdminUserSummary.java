package com.clubplatform.user.dto;

import com.clubplatform.user.entity.UserRole;

public record AdminUserSummary(long id, String intraLogin, String displayName, UserRole role) {}
