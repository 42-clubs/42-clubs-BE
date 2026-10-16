package com.clubplatform.notice.dto;

import com.clubplatform.notice.entity.NoticeVisibility;
import java.time.Instant;

public record NoticeResponse(
        long id,
        long clubId,
        long authorId,
        String title,
        String content,
        NoticeVisibility visibility,
        Instant createdAt) {}
