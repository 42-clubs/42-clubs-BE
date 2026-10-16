package com.clubplatform.activity.dto;

public record MyActivity(
        long activityId,
        long clubId,
        String title,
        java.time.Instant startsAt,
        long organizerId,
        String status,
        String attendanceStatus) {}
