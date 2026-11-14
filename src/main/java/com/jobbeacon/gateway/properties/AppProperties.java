package com.jobbeacon.gateway.properties;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties("app")
public record AppProperties(
        Firebase firebase,
    Cors cors,
    List<String> publicPaths,
    List<String> publicGetPaths) {

  public static final List<String> DEFAULT_PUBLIC_PATHS =
      List.of(
          "/actuator/health",
          "/actuator/info",
          "/api/v1/health",
          "/swagger-ui.html",
          "/swagger-ui/**",
          "/v3/api-docs/**",
          "/api/v1/v3/api-docs/**",
          "/api/rcm/v3/api-docs/**");

  public static final List<String> DEFAULT_PUBLIC_GET_PATHS =
      List.of(
          "/api/v1/jobs",
          "/api/v1/jobs/**",
          "/api/v1/locations/**",
          "/api/v1/categories",
          "/api/v1/companies",
          "/api/v1/companies/**");

  public AppProperties {
    if (firebase == null || !StringUtils.hasText(firebase.projectId())) {
      throw new IllegalStateException("app.firebase.project-id must not be blank");
    }
    cors = cors == null ? new Cors(List.of()) : cors;
    publicPaths =
        publicPaths == null || publicPaths.isEmpty()
            ? DEFAULT_PUBLIC_PATHS
            : List.copyOf(publicPaths);
    publicGetPaths =
        publicGetPaths == null || publicGetPaths.isEmpty()
            ? DEFAULT_PUBLIC_GET_PATHS
            : List.copyOf(publicGetPaths);
  }

  public record Firebase(String projectId) {}

  public record Cors(List<String> allowedOrigins) {

    public Cors {
      allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
  }
}
