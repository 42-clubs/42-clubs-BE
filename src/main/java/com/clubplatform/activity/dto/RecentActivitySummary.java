package com.clubplatform.activity.dto;

import java.time.Instant;

/** 동아리 상세 조합에 제공할 최근 활동의 공개 요약. */
public record RecentActivitySummary(
        long id,
        String title,
        Instant startsAt,
        long currentParticipantCount,
        long presentParticipantCount) {}
