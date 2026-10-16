package com.clubplatform.gathering.dto;

import java.time.Instant;

public record SessionResponse(
        long sessionId,
        long gatheringId,
        String title,
        Instant startsAt,
        Instant endsAt,
        Instant recruitmentOpensAt,
        Instant recruitmentClosesAt,
        String location,
        int capacity,
        long currentParticipantCount,
        long remainingCapacity,
        boolean recruiting,
        String dDay,
        boolean cancelled) {}
