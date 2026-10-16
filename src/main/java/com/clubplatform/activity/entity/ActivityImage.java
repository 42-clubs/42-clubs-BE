package com.clubplatform.activity.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "activity_images")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ActivityImage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private long activityId;

    @Column(nullable = false, columnDefinition = "text")
    private String storageKey;

    @Column(nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private Instant createdAt;

    public ActivityImage(long activityId, String storageKey, int sortOrder, Instant now) {
        this.activityId = activityId;
        this.storageKey = storageKey;
        this.sortOrder = sortOrder;
        createdAt = now;
    }
}
