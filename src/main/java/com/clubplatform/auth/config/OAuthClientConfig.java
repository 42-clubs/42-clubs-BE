package com.clubplatform.auth.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.util.Assert;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.auth.enabled", havingValue = "true")
public class OAuthClientConfig {
    @Bean
    public ClientRegistrationRepository clientRegistrationRepository(AuthProperties properties) {
        Assert.hasText(properties.clientId(), "FT_CLIENT_ID가 필요합니다.");
        Assert.hasText(properties.clientSecret(), "FT_CLIENT_SECRET이 필요합니다.");
        ClientRegistration registration =
                ClientRegistration.withRegistrationId("42")
                        .clientId(properties.clientId())
                        .clientSecret(properties.clientSecret())
                        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                        .redirectUri(properties.redirectUri())
                        .scope("public")
                        .authorizationUri("https://api.intra.42.fr/oauth/authorize")
                        .tokenUri("https://api.intra.42.fr/oauth/token")
                        .userInfoUri("https://api.intra.42.fr/v2/me")
                        .userNameAttributeName("id")
                        .clientName("42 Gyeongsan")
                        .build();
        return new InMemoryClientRegistrationRepository(registration);
    }
}
