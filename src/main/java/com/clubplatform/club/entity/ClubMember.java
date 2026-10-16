package com.clubplatform.club.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "club_members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClubMember {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private long userId;

    @Column(nullable = false)
    private long clubId;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false)
    private long approvedBy;

    @Column(nullable = false)
    private Instant joinedAt;

    private Instant leftAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public ClubMember(long userId, long clubId, long actorId, Instant now) {
        this.userId = userId;
        this.clubId = clubId;
        createdAt = now;
        register(actorId, now);
    }

    public void register(long actorId, Instant now) {
        if ("ACTIVE".equals(status)) return;
        status = "ACTIVE";
        approvedBy = actorId;
        joinedAt = now;
        leftAt = null;
        updatedAt = now;
    }
}
