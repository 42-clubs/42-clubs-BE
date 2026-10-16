package com.clubplatform.activity.storage;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "image_uploads")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ImageUpload {
    @Id
    @Column(columnDefinition = "text")
    private String storageKey;

    @Column(nullable = false)
    private Instant createdAt;

    public ImageUpload(String key, Instant now) {
        storageKey = key;
        createdAt = now;
    }
}
