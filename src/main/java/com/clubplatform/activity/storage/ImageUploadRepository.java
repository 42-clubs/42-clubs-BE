package com.clubplatform.activity.storage;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImageUploadRepository extends JpaRepository<ImageUpload, String> {
    List<ImageUpload> findByCreatedAtBeforeOrderByCreatedAt(Instant time, Pageable page);

    @org.springframework.data.jpa.repository.Lock(
            jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query(
            "select u from ImageUpload u where u.storageKey=:key")
    java.util.Optional<ImageUpload> findForUpdate(
            @org.springframework.data.repository.query.Param("key") String key);
}
