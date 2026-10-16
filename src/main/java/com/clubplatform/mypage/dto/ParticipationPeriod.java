package com.clubplatform.mypage.dto;

import java.time.Instant;

public enum ParticipationPeriod {
    UPCOMING,
    PAST,
    ALL;

    public boolean includes(Instant startsAt, Instant now) {
        return this == ALL || (this == UPCOMING ? startsAt.isAfter(now) : !startsAt.isAfter(now));
    }
}
