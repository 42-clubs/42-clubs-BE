package com.clubplatform.activity.controller;

import com.clubplatform.activity.repository.ActivityImageRepository;
import com.clubplatform.activity.storage.LocalPhotoStorage;
import com.clubplatform.global.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.photos.mode", havingValue = "local", matchIfMissing = true)
public class LocalPhotoController {
    private final LocalPhotoStorage storage;
    private final ActivityImageRepository images;

    @GetMapping("/api/v1/photos/activities/{filename:[0-9a-f-]{36}\\.(?:jpeg|png|webp)}")
    public ResponseEntity<byte[]> photo(@PathVariable String filename) {
        String key = "activities/" + filename;
        if (!images.isPublished(key)) throw new ResourceNotFoundException("사진을 찾을 수 없습니다.");
        String extension = filename.substring(filename.lastIndexOf('.') + 1);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("image/" + extension))
                .cacheControl(CacheControl.noStore())
                .body(storage.read(key));
    }
}
