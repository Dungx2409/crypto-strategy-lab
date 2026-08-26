package com.cryptolab.api.account;

import static org.springframework.security.config.Customizer.withDefaults;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.cryptolab.api.shared.ApiError;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
public class ApiSecurityConfiguration {

    @Bean
    SecurityFilterChain apiSecurityFilterChain(
            HttpSecurity http, ObjectMapper objectMapper, Clock marketDataClock) throws Exception {
        var csrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
        return http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/",
                                "/index.html",
                                "/*.js",
                                "/*.css",
                                "/vendor/**",
                                "/favicon.ico",
                                "/ws/**",
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/csrf")
                        .permitAll()
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/market/**",
                                "/api/v1/news/**",
                                "/api/v1/system/**",
                                "/api/v1/strategies",
                                "/api/v1/search-runs/capabilities",
                                "/api/v1/public/**",
                                "/actuator/health/**")
                        .permitAll()
                        .requestMatchers("/api/v1/admin/**", "/api/v1/news/collect")
                        .hasRole("ADMIN")
                        .anyRequest()
                        .authenticated())
                .csrf(configurer -> configurer
                        .csrfTokenRepository(csrf)
                        .ignoringRequestMatchers(
                                "/api/v1/auth/register", "/api/v1/auth/login"))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> writeError(
                                response,
                                objectMapper,
                                marketDataClock,
                                HttpStatus.UNAUTHORIZED,
                                "AUTHENTICATION_REQUIRED",
                                "Authentication is required"))
                        .accessDeniedHandler((request, response, exception) -> writeError(
                                response,
                                objectMapper,
                                marketDataClock,
                                HttpStatus.FORBIDDEN,
                                "ACCESS_DENIED",
                                "This account cannot access the requested resource")))
                .formLogin(configurer -> configurer.disable())
                .httpBasic(configurer -> configurer.disable())
                .logout(configurer -> configurer.disable())
                .cors(withDefaults())
                .build();
    }

    private static void writeError(
            jakarta.servlet.http.HttpServletResponse response,
            ObjectMapper objectMapper,
            Clock clock,
            HttpStatus status,
            String code,
            String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getOutputStream(),
                new ApiError(
                        Instant.now(clock),
                        status.value(),
                        code,
                        message,
                        UUID.randomUUID().toString()));
    }
}
