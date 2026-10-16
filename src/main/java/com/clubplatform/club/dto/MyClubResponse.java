package com.clubplatform.club.dto;

import com.clubplatform.club.entity.OperationStatus;

public record MyClubResponse(
        long clubId,
        String name,
        OperationStatus operationStatus,
        boolean isMember,
        boolean isManager,
        ClubPermissions permissions) {}
