package com.clubplatform.gathering.dto;

public record GatheringResponse(
        long id,
        long organizerId,
        String title,
        String content,
        String representativeImageUrl,
        SessionResponse representativeSession) {}
