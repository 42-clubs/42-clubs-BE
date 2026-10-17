package com.clubplatform.activity.service;

import com.clubplatform.activity.dto.ActivityRequest;
import com.clubplatform.activity.storage.*;
import com.clubplatform.club.service.ClubPermissionService;
import java.time.Clock;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ActivityCreationService {
    private final ClubPermissionService clubs;
    private final PhotoValidator validator;
    private final PhotoStorage storage;
    private final ImageUploadJournal journal;
    private final PhotoCleanup cleanup;
    private final ActivityWriter writer;
    private final Clock clock;

    public long create(
            long actorId, long clubId, ActivityRequest request, List<MultipartFile> files) {
        clubs.requireManager(actorId, clubId);
        request.validate(clock.instant());
        if (!request.allowExternalParticipants()) clubs.requireActiveMember(actorId, clubId);
        var photos = validator.validate(files);
        var keys = new ArrayList<String>();
        try {
            for (var photo : photos) {
                String key = "activities/" + UUID.randomUUID() + "." + photo.extension();
                journal.reserve(key);
                keys.add(key);
                storage.put(key, photo.bytes(), photo.contentType());
            }
            return writer.create(actorId, clubId, request, keys);
        } catch (RuntimeException e) {
            for (String key : keys) cleanup.remove(key);
            throw e;
        }
    }
}
