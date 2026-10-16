package com.clubplatform.activity;

import static org.assertj.core.api.Assertions.*;

import com.clubplatform.activity.storage.*;
import com.sun.net.httpserver.HttpServer;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class PhotoStorageTests {
    @Test
    void s3AdapterSendsSignedUploadDeleteAndProducesUsableGetUrl() throws Exception {
        var methods = new CopyOnWriteArrayList<String>();
        var authorizations = new CopyOnWriteArrayList<String>();
        var server =
                HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext(
                "/",
                exchange -> {
                    methods.add(
                            exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
                    authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
                    exchange.getRequestBody().readAllBytes();
                    exchange.sendResponseHeaders(200, -1);
                    exchange.close();
                });
        server.start();
        var endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        var credentials =
                StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test-access", "test-secret"));
        try (var client =
                        S3Client.builder()
                                .endpointOverride(endpoint)
                                .forcePathStyle(true)
                                .region(Region.AP_NORTHEAST_2)
                                .credentialsProvider(credentials)
                                .build();
                var presigner =
                        S3Presigner.builder()
                                .region(Region.AP_NORTHEAST_2)
                                .credentialsProvider(credentials)
                                .build()) {
            var storage =
                    new S3PhotoStorage(
                            client,
                            presigner,
                            new PhotoStorageProperties("test-bucket", "ap-northeast-2", ""));
            storage.put("activities/test.png", new byte[] {1, 2, 3}, "image/png");
            storage.delete("activities/test.png");
            assertThat(methods)
                    .containsExactly(
                            "PUT /test-bucket/activities/test.png",
                            "DELETE /test-bucket/activities/test.png");
            assertThat(authorizations).allMatch(h -> h.startsWith("AWS4-HMAC-SHA256"));
            var url = URI.create(storage.url("activities/test.png"));
            assertThat(url.getScheme()).isEqualTo("https");
            assertThat(url.getHost()).contains("test-bucket");
            assertThat(url.getRawQuery()).contains("X-Amz-Expires=900", "X-Amz-Signature=");
            var publicStorage =
                    new S3PhotoStorage(
                            client,
                            presigner,
                            new PhotoStorageProperties(
                                    "test-bucket", "ap-northeast-2", "https://images.example/"));
            assertThat(publicStorage.url("activities/test.png"))
                    .isEqualTo("https://images.example/activities/test.png");
            var unavailable =
                    new S3PhotoStorage(
                            client,
                            presigner,
                            new PhotoStorageProperties("", "ap-northeast-2", ""));
            assertThatThrownBy(() -> unavailable.put("test", new byte[0], "image/png"))
                    .isInstanceOf(StorageUnavailableException.class);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void validatorDecodesJpegPngAndWebpAndRejectsGif() throws Exception {
        var validator = new PhotoValidator();
        for (String format : List.of("jpeg", "png")) {
            var bytes = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), format, bytes);
            var result =
                    validator.validate(
                            List.of(
                                    new MockMultipartFile(
                                            "images",
                                            "fake.exe",
                                            "text/plain",
                                            bytes.toByteArray())));
            assertThat(result.get(0).contentType()).isEqualTo("image/" + format);
        }
        byte[] webp =
                Base64.getDecoder()
                        .decode("UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEADsD+JaQAA3AAAAAA");
        assertThat(
                        validator
                                .validate(
                                        List.of(
                                                new MockMultipartFile(
                                                        "images", "test.webp", "image/webp", webp)))
                                .get(0)
                                .contentType())
                .isEqualTo("image/webp");
        var gif = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "gif", gif);
        assertThatThrownBy(
                        () ->
                                validator.validate(
                                        List.of(
                                                new MockMultipartFile(
                                                        "images",
                                                        "fake.png",
                                                        "image/png",
                                                        gif.toByteArray()))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
