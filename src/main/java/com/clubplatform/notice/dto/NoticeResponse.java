package com.clubplatform.notice.dto;

import java.time.Instant;

public record NoticeResponse(
        long id, long clubId, long authorId, String title, String content, Instant createdAt) {}
