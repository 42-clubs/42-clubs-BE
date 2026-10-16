package com.clubplatform.notice.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "notices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private long clubId;

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

    public Notice(long clubId, long authorId, String title, String content, Instant now) {
        this.clubId = clubId;
        this.authorId = authorId;
        this.title = title;
        this.content = content;
        createdAt = now;
        updatedAt = now;
    }
}
