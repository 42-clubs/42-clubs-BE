package com.clubplatform.gathering.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "gathering_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GatheringSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private long gatheringId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false)
    private Instant startsAt;

    @Column(nullable = false)
    private Instant endsAt;

    @Column(nullable = false)
    private Instant recruitmentOpensAt;

    @Column(nullable = false)
    private Instant recruitmentClosesAt;

    @Column(nullable = false, length = 255)
    private String location;

    @Column(nullable = false)
    private int capacity;

    private Instant cancelledAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public GatheringSession(
            long gatheringId,
            String title,
            Instant startsAt,
            Instant endsAt,
            Instant closesAt,
            String location,
            int capacity,
            Instant now) {
        this.gatheringId = gatheringId;
        this.title = title;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        recruitmentOpensAt = now;
        recruitmentClosesAt = closesAt;
        this.location = location;
        this.capacity = capacity;
        createdAt = now;
        updatedAt = now;
    }

    public void requireRecruiting(Instant now) {
        if (cancelledAt != null
                || now.isBefore(recruitmentOpensAt)
                || !now.isBefore(recruitmentClosesAt)
                || !now.isBefore(startsAt))
            throw new com.clubplatform.global.exception.ConflictException(
                    "RECRUITMENT_CLOSED", "모집이 마감되었습니다.");
    }
}
