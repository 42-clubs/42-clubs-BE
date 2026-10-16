package com.clubplatform.activity.storage;

import java.time.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PhotoCleanup {
    private final PhotoCleanupWorker worker;
    private final ImageUploadRepository uploads;
    private final Clock clock;

    public void remove(String key) {
        try {
            worker.remove(key, null);
        } catch (RuntimeException e) {
            log.warn("사진 정리 재시도 대기: key={}", key);
        }
    }

    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void retry() {
        for (var upload :
                uploads.findByCreatedAtBeforeOrderByCreatedAt(
                        clock.instant().minus(Duration.ofMinutes(10)), PageRequest.of(0, 100)))
            try {
                worker.remove(
                        upload.getStorageKey(), clock.instant().minus(Duration.ofMinutes(10)));
            } catch (RuntimeException e) {
                log.warn("사진 정리 재시도 대기: key={}", upload.getStorageKey());
            }
    }
}
