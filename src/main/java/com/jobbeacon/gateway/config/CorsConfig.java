package com.jobbeacon.gateway.config;

import com.jobbeacon.gateway.properties.AppProperties;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class CorsConfig {

  private static final List<String> ALLOWED_METHODS =
      List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
  private static final long MAX_AGE_SECONDS = 3600L;

  private final AppProperties appProperties;

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    List<String> origins = appProperties.cors().allowedOrigins();
    log.info("(corsConfigurationSource) allowed origins: {}", origins);

    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(origins);
    config.setAllowedMethods(ALLOWED_METHODS);
    config.setAllowedHeaders(List.of(CorsConfiguration.ALL));
    config.setAllowCredentials(true);
    config.setMaxAge(MAX_AGE_SECONDS);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }
}
