package com.clubplatform.club.dto;

import com.clubplatform.club.entity.OperationStatus;

public record ClubPermissions(
        boolean isMember,
        boolean isManager,
        boolean canCreateActivity,
        boolean canCreateNotice,
        boolean canViewMembers,
        boolean canReadPrivateNotices) {
    public static ClubPermissions of(
            boolean member, boolean manager, boolean admin, OperationStatus status) {
        boolean active = status == OperationStatus.ACTIVE;
        return new ClubPermissions(
                member,
                manager,
                active && manager,
                active && (manager || admin),
                member,
                member || manager || admin);
    }
}
