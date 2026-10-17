package com.clubplatform.activity.dto;

import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;

public record ActivityRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 50000) String content,
        @NotNull OffsetDateTime startsAt,
        @NotNull OffsetDateTime recruitmentClosesAt,
        @Min(1) int capacity,
        @NotNull Boolean allowExternalParticipants) {
    public void validate(java.time.Instant now) {
        if (allowExternalParticipants == null)
            throw new IllegalArgumentException("외부인 참가 허용 여부를 선택해 주세요.");
        if (!startsAt.toInstant().isAfter(now)
                || !recruitmentClosesAt.toInstant().isAfter(now)
                || !recruitmentClosesAt.isBefore(startsAt))
            throw new IllegalArgumentException("개최·마감 시각을 확인해 주세요.");
    }
}
