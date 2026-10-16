package com.clubplatform.activity.storage;

import java.net.URI;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Component
@RequiredArgsConstructor
public class S3PhotoStorage implements PhotoStorage {
    private final S3Client client;
    private final S3Presigner presigner;
    private final PhotoStorageProperties properties;

    public void put(String key, byte[] bytes, String contentType) {
        requireBucket();
        try {
            client.putObject(
                    PutObjectRequest.builder()
                            .bucket(properties.bucket())
                            .key(key)
                            .contentType(contentType)
                            .serverSideEncryption(ServerSideEncryption.AES256)
                            .build(),
                    RequestBody.fromBytes(bytes));
        } catch (RuntimeException e) {
            throw new StorageUnavailableException("사진 저장소 업로드에 실패했습니다.", e);
        }
    }

    public void delete(String key) {
        requireBucket();
        try {
            client.deleteObject(
                    DeleteObjectRequest.builder().bucket(properties.bucket()).key(key).build());
        } catch (RuntimeException e) {
            throw new StorageUnavailableException("사진 정리에 실패했습니다.", e);
        }
    }

    public String url(String key) {
        requireBucket();
        try {
            if (properties.publicBaseUrl() != null && !properties.publicBaseUrl().isBlank()) {
                var base = URI.create(properties.publicBaseUrl());
                if (!"https".equals(base.getScheme())
                        || base.getHost() == null
                        || base.getQuery() != null
                        || base.getFragment() != null)
                    throw new IllegalArgumentException("이미지 기본 URL은 HTTPS 주소여야 합니다.");
                return properties.publicBaseUrl().replaceAll("/+$", "") + "/" + key;
            }
            return presigner
                    .presignGetObject(
                            r ->
                                    r.signatureDuration(Duration.ofMinutes(15))
                                            .getObjectRequest(
                                                    g -> g.bucket(properties.bucket()).key(key)))
                    .url()
                    .toExternalForm();
        } catch (RuntimeException e) {
            throw new StorageUnavailableException("이미지 URL을 생성할 수 없습니다.", e);
        }
    }

    private void requireBucket() {
        if (properties.bucket() == null || properties.bucket().isBlank())
            throw new StorageUnavailableException("사진 저장소 설정이 필요합니다.");
    }
}
