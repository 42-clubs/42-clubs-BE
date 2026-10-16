package com.clubplatform.gathering.dto;

public record MySession(
        long sessionId,
        long gatheringId,
        String gatheringTitle,
        String title,
        java.time.Instant startsAt,
        java.time.Instant endsAt,
        long organizerId,
        String status,
        String attendanceStatus) {}
