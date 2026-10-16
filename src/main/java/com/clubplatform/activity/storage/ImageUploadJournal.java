package com.clubplatform.activity.storage;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
public class ImageUploadJournal {
    private final ImageUploadRepository uploads;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reserve(String key) {
        uploads.saveAndFlush(new ImageUpload(key, clock.instant()));
    }
}
