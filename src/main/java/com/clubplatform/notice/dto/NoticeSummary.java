package com.clubplatform.notice.dto;

import com.clubplatform.notice.entity.NoticeVisibility;
import java.time.Instant;

public record NoticeSummary(
        long id, long clubId, String title, NoticeVisibility visibility, Instant createdAt) {}
