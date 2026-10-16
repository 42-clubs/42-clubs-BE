package com.clubplatform.activity.dto;

import java.time.Instant;
import java.util.List;

public record ActivityResponse(
        long id,
        long clubId,
        long organizerId,
        String title,
        String content,
        Instant startsAt,
        Instant recruitmentClosesAt,
        int capacity,
        long currentParticipantCount,
        long remainingCapacity,
        boolean recruiting,
        String dDay,
        String representativeImageUrl,
        List<ImageResponse> images) {}
