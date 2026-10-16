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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NoticeVisibility visibility;

    public Notice(
            long clubId,
            long authorId,
            String title,
            String content,
            NoticeVisibility visibility,
            Instant now) {
        this.clubId = clubId;
        this.authorId = authorId;
        this.title = title;
        this.content = content;
        this.visibility = visibility == null ? NoticeVisibility.MEMBERS_ONLY : visibility;
        createdAt = now;
        updatedAt = now;
    }
}
