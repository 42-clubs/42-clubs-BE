package com.clubplatform.announcement.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "announcements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Announcement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private long authorId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant deletedAt;

    public Announcement(long actorId, String title, String content, Instant now) {
        authorId = actorId;
        this.title = title;
        this.content = content;
        createdAt = now;
        updatedAt = now;
    }

    public void delete(Instant now) {
        if (deletedAt == null) {
            deletedAt = now;
            updatedAt = now;
        }
    }
}
