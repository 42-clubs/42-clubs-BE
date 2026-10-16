package com.clubplatform.notice.dto;

import java.time.Instant;

public record NoticeSummary(long id, long clubId, String title, Instant createdAt) {}
