package com.clubplatform.gathering.dto;

import jakarta.validation.constraints.*;
import java.time.*;

public record SessionRequest(
        @NotBlank @Size(max = 200) String title,
        @NotNull OffsetDateTime startsAt,
        @NotNull OffsetDateTime endsAt,
        @NotNull OffsetDateTime recruitmentClosesAt,
        @NotBlank @Size(max = 255) String location,
        @Min(1) int capacity) {
    public void validate(Instant now) {
        if (startsAt.toInstant().isBefore(now.plus(Duration.ofHours(2)))
                || !endsAt.isAfter(startsAt)
                || !recruitmentClosesAt.toInstant().isAfter(now)
                || !recruitmentClosesAt.isBefore(startsAt))
            throw new IllegalArgumentException("회차 개최·종료·모집 시각을 확인해 주세요.");
    }
}
