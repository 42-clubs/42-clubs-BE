package com.clubplatform.club.dto;

import com.clubplatform.club.entity.OperationStatus;
import java.util.List;

public record MembershipResponse(long userId, List<ClubMembership> clubs, long activeClubCount) {
    public record ClubMembership(long id, String name, OperationStatus operationStatus) {}
}
