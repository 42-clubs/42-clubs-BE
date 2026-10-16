package com.clubplatform.mypage.dto;

import java.time.Instant;
import java.util.List;

public record MyParticipationGroup(String type, long groupId, String name, List<Item> items) {
    public record Item(
            Long activityId,
            Long sessionId,
            String title,
            Instant startsAt,
            Instant endsAt,
            String status,
            String attendanceStatus,
            boolean isOrganizer) {}
}
