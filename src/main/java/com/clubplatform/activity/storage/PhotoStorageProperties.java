package com.clubplatform.activity.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.photos")
public record PhotoStorageProperties(String bucket, String region, String publicBaseUrl) {}
