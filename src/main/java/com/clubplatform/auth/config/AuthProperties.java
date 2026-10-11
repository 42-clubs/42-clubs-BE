package com.clubplatform.auth.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.auth")
public record AuthProperties(
        boolean enabled,
        String clientId,
        String clientSecret,
        String redirectUri,
        String successUrl,
        List<String> allowedOrigins) {}
