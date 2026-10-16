package com.clubplatform.activity.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "activities")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Activity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private long clubId;

    @Column(nullable = false)
    private long authorId;

    @Column(nullable = false)
    private long organizerId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(nullable = false)
    private Instant startsAt;

    @Column(nullable = false)
    private Instant recruitmentClosesAt;

    @Column(nullable = false)
    private int capacity;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant deletedAt;

    public Activity(
            long clubId,
            long actorId,
            String title,
            String content,
            Instant startsAt,
            Instant closesAt,
            int capacity,
            Instant now) {
        this.clubId = clubId;
        authorId = actorId;
        organizerId = actorId;
        this.title = title;
        this.content = content;
        this.startsAt = startsAt;
        recruitmentClosesAt = closesAt;
        this.capacity = capacity;
        createdAt = now;
        updatedAt = now;
    }

    public void requireRecruiting(Instant now) {
        if (!now.isBefore(recruitmentClosesAt) || !now.isBefore(startsAt))
            throw new com.clubplatform.global.exception.ConflictException(
                    "RECRUITMENT_CLOSED", "모집이 마감되었습니다.");
    }
}
