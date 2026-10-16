package com.clubplatform.activity;

import static org.assertj.core.api.Assertions.*;

import com.clubplatform.activity.storage.*;
import java.nio.file.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalPhotoStorageTests {
    @TempDir Path directory;

    @Test
    void persistsAcrossInstancesAndDeletesIdempotently() throws Exception {
        var storage = new LocalPhotoStorage(directory.toString(), "http://localhost:8080/");
        String key = "activities/" + UUID.randomUUID() + ".png";
        storage.put(key, new byte[] {1, 2, 3}, "image/png");
        assertThat(storage.url(key)).isEqualTo("http://localhost:8080/api/v1/photos/" + key);
        assertThat(new LocalPhotoStorage(directory.toString(), "http://localhost:8080").read(key))
                .containsExactly(1, 2, 3);
        assertThatThrownBy(() -> storage.put(key, new byte[] {4}, "image/png"))
                .isInstanceOf(StorageUnavailableException.class);
        assertThat(storage.read(key)).containsExactly(1, 2, 3);
        storage.delete(key);
        storage.delete(key);
        assertThat(directory.resolve(key)).doesNotExist();
    }

    @Test
    void rejectsTraversalAndSymlinkAndReportsUnwritableDirectory() throws Exception {
        var storage = new LocalPhotoStorage(directory.toString(), "http://localhost:8080");
        assertThatThrownBy(() -> storage.read("activities/../../secret.png"))
                .isInstanceOf(IllegalArgumentException.class);
        String key = "activities/" + UUID.randomUUID() + ".png";
        Files.createSymbolicLink(directory.resolve("activities"), directory);
        assertThatThrownBy(() -> storage.put(key, new byte[] {1}, "image/png"))
                .isInstanceOf(StorageUnavailableException.class);
        Path file = Files.write(directory.resolve("file"), new byte[0]);
        var broken = new LocalPhotoStorage(file.toString(), "http://localhost:8080");
        assertThatThrownBy(() -> broken.put(key, new byte[] {1}, "image/png"))
                .isInstanceOf(StorageUnavailableException.class);
    }
}
