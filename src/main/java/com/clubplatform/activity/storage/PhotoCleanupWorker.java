package com.clubplatform.activity.storage;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 정리와 활동 연결이 같은 파일 키를 동시에 처리하지 않도록 저널 행을 잠근다. */
@Service
@RequiredArgsConstructor
public class PhotoCleanupWorker {
    private final ImageUploadRepository uploads;
    private final PhotoStorage storage;

    @Transactional
    public void remove(String key, Instant olderThan) {
        var pending = uploads.findForUpdate(key);
        if (pending.isEmpty()) return;
        if (olderThan != null && !pending.get().getCreatedAt().isBefore(olderThan)) return;
        storage.delete(key);
        uploads.delete(pending.get());
    }
}
