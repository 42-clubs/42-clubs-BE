package com.clubplatform.activity.storage;

import java.io.IOException;
import java.nio.file.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.photos.mode", havingValue = "local", matchIfMissing = true)
public class LocalPhotoStorage implements PhotoStorage {
    private final Path root;
    private final String baseUrl;

    public LocalPhotoStorage(
            @Value("${app.photos.local-directory:./data/photos}") String directory,
            @Value("${app.photos.local-base-url:http://localhost:8080}") String baseUrl) {
        this.root = Path.of(directory).toAbsolutePath().normalize();
        var uri = java.net.URI.create(baseUrl);
        if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                || uri.getHost() == null
                || uri.getQuery() != null
                || uri.getFragment() != null)
            throw new IllegalArgumentException("사진 조회 기본 URL은 HTTP(S) 주소여야 합니다.");
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    private Path path(String key) {
        if (!key.matches("activities/[0-9a-f-]{36}\\.(jpeg|png|webp)"))
            throw new IllegalArgumentException("잘못된 사진 키입니다.");
        Path file = root.resolve(key);
        if (Files.isSymbolicLink(root)
                || Files.isSymbolicLink(file.getParent())
                || Files.isSymbolicLink(file))
            throw new StorageUnavailableException("사진 저장 경로에 심볼릭 링크를 사용할 수 없습니다.");
        return file;
    }

    public void put(String key, byte[] bytes, String contentType) {
        Path file = path(key);
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new StorageUnavailableException("로컬 사진 저장에 실패했습니다.", e);
        }
    }

    public void delete(String key) {
        try {
            Files.deleteIfExists(path(key));
        } catch (IOException e) {
            throw new StorageUnavailableException("로컬 사진 정리에 실패했습니다.", e);
        }
    }

    public byte[] read(String key) {
        try {
            return Files.readAllBytes(path(key));
        } catch (NoSuchFileException e) {
            throw new com.clubplatform.global.exception.ResourceNotFoundException("사진을 찾을 수 없습니다.");
        } catch (IOException e) {
            throw new StorageUnavailableException("로컬 사진 조회에 실패했습니다.", e);
        }
    }

    public String url(String key) {
        path(key);
        return baseUrl + "/api/v1/photos/" + key;
    }
}
