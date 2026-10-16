package com.clubplatform.announcement.dto;

import java.time.Instant;

public record AnnouncementResponse(
        long id, long authorId, String title, String content, Instant createdAt) {}
