package com.clubplatform.gathering.dto;

import java.time.Instant;

public record ParticipantResponse(
        long userId,
        String intraLogin,
        String displayName,
        String status,
        String attendanceStatus,
        Long attendanceCheckedBy,
        Instant attendanceCheckedAt) {}
