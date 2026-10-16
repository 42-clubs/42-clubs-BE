package com.clubplatform.auth.config;

import com.clubplatform.auth.security.CurrentUserFilter;
import com.clubplatform.auth.security.OAuthLoginFailureHandler;
import com.clubplatform.auth.service.FortyTwoOAuth2UserService;
import com.clubplatform.global.exception.ApiErrorResponse;
import com.clubplatform.user.service.UserAccountService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthProperties.class)
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthProperties properties,
            FortyTwoOAuth2UserService oauthUsers,
            UserAccountService users,
            ObjectMapper mapper,
            OAuthLoginFailureHandler oauthFailureHandler)
            throws Exception {
        var cors = new CorsConfiguration();
        cors.setAllowedOrigins(properties.allowedOrigins());
        cors.setAllowCredentials(true);
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Content-Type", "X-CSRF-TOKEN"));
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        http.cors(config -> config.configurationSource(source))
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(
                        authorize ->
                                authorize
                                        .requestMatchers(
                                                HttpMethod.GET,
                                                "/api/v1/clubs",
                                                "/api/v1/clubs/{clubId}",
                                                "/api/v1/auth/csrf",
                                                "/api/v1/clubs/{clubId}/notices",
                                                "/api/v1/notices/{noticeId}")
                                        .permitAll()
                                        .requestMatchers("/", "/error")
                                        .permitAll()
                                        .requestMatchers("/api/v1/admin/**")
                                        .hasAnyRole("ADMIN", "SUPER_ADMIN")
                                        .anyRequest()
                                        .authenticated())
                .exceptionHandling(
                        errors ->
                                errors.authenticationEntryPoint(
                                                (request, response, exception) ->
                                                        error(
                                                                mapper,
                                                                response,
                                                                401,
                                                                "UNAUTHENTICATED",
                                                                "로그인이 필요합니다."))
                                        .accessDeniedHandler(
                                                (request, response, exception) ->
                                                        error(
                                                                mapper,
                                                                response,
                                                                403,
                                                                "ACCESS_DENIED",
                                                                "권한 또는 CSRF 토큰을 확인해 주세요.")))
                .addFilterBefore(new CurrentUserFilter(users), AuthorizationFilter.class)
                .logout(
                        logout ->
                                logout.logoutUrl("/api/v1/auth/logout")
                                        .deleteCookies("JSESSIONID")
                                        .logoutSuccessHandler(
                                                (request, response, authentication) ->
                                                        response.setStatus(204))
                                        .permitAll());
        if (properties.enabled()) {
            http.oauth2Login(
                    oauth ->
                            oauth.userInfoEndpoint(info -> info.userService(oauthUsers))
                                    .authorizedClientRepository(
                                            new DiscardAuthorizedClientRepository())
                                    .successHandler(
                                            (request, response, authentication) ->
                                                    response.sendRedirect(properties.successUrl()))
                                    .failureHandler(oauthFailureHandler));
        }
        return http.build();
    }

    private static void error(
            ObjectMapper mapper,
            HttpServletResponse response,
            int status,
            String code,
            String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        mapper.writeValue(response.getOutputStream(), new ApiErrorResponse(code, message));
    }

    /** 로그인 이후 42 API 토큰을 사용하지 않으므로 서버에 저장하지 않는다. */
    private static class DiscardAuthorizedClientRepository
            implements OAuth2AuthorizedClientRepository {
        @Override
        public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(
                String registrationId, Authentication principal, HttpServletRequest request) {
            return null;
        }

        @Override
        public void saveAuthorizedClient(
                OAuth2AuthorizedClient client,
                Authentication principal,
                HttpServletRequest request,
                HttpServletResponse response) {}

        @Override
        public void removeAuthorizedClient(
                String registrationId,
                Authentication principal,
                HttpServletRequest request,
                HttpServletResponse response) {}
    }
}
