package com.clubplatform.announcement.dto;

import java.time.Instant;

public record AnnouncementSummary(long id, String title, Instant createdAt) {}
