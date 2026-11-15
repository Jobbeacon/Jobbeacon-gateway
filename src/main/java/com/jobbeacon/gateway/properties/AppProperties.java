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
          "/jobbeacon-be/actuator/health",
          "/jobbeacon-be/api/v1/health",
          "/jobbeacon-be/swagger-ui.html",
          "/jobbeacon-be/swagger-ui/**",
          "/jobbeacon-be/v3/api-docs/**",
          "/jobbeacon-recommendation/actuator/health",
          "/jobbeacon-recommendation/api/v1/health");

  public static final List<String> DEFAULT_PUBLIC_GET_PATHS =
      List.of(
          "/jobbeacon-be/api/v1/jobs",
          "/jobbeacon-be/api/v1/jobs/**",
          "/jobbeacon-be/api/v1/locations/**",
          "/jobbeacon-be/api/v1/categories",
          "/jobbeacon-be/api/v1/companies",
          "/jobbeacon-be/api/v1/companies/**");

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
