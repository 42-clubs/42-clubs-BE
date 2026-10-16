package com.clubplatform.activity.storage;

import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(PhotoStorageProperties.class)
public class PhotoStorageConfig {
    @Bean(destroyMethod = "close")
    S3Client photoS3Client(PhotoStorageProperties p) {
        return S3Client.builder()
                .region(Region.of(p.region()))
                .overrideConfiguration(
                        ClientOverrideConfiguration.builder()
                                .apiCallTimeout(Duration.ofSeconds(30))
                                .apiCallAttemptTimeout(Duration.ofSeconds(10))
                                .build())
                .build();
    }

    @Bean(destroyMethod = "close")
    S3Presigner photoPresigner(PhotoStorageProperties p) {
        return S3Presigner.builder().region(Region.of(p.region())).build();
    }
}
