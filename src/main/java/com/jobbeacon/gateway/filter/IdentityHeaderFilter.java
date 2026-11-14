package com.jobbeacon.gateway.filter;

import com.jobbeacon.gateway.constant.GatewayHeaders;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class IdentityHeaderFilter implements GlobalFilter, Ordered {

  private static final String USER_PREFIX_LOWER =
      GatewayHeaders.USER_PREFIX.toLowerCase(Locale.ROOT);

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    return exchange
        .getPrincipal()
        .filter(JwtAuthenticationToken.class::isInstance)
        .map(principal -> Optional.of(((JwtAuthenticationToken) principal).getToken()))
        .defaultIfEmpty(Optional.empty())
        .flatMap(jwt -> chain.filter(mutate(exchange, jwt.orElse(null))));
  }

  private ServerWebExchange mutate(ServerWebExchange exchange, Jwt jwt) {
    return exchange
        .mutate()
        .request(request -> request.headers(headers -> rewriteHeaders(headers, jwt)))
        .build();
  }

  private void rewriteHeaders(HttpHeaders headers, Jwt jwt) {
    List<String> toRemove =
        headers.keySet().stream()
            .filter(
name -> name.toLowerCase(Locale.ROOT).startsWith(USER_PREFIX_LOWER))
            .toList();
    toRemove.forEach(headers::remove);
    headers.remove(HttpHeaders.AUTHORIZATION);

    if (jwt != null) {
      setIfPresent(headers, GatewayHeaders.USER_ID, jwt.getSubject());
      String role = jwt.getClaimAsString("role");
      headers.set(
          GatewayHeaders.USER_ROLE, StringUtils.hasText(role) ? role : GatewayHeaders.DEFAULT_ROLE);
      setIfPresent(headers, GatewayHeaders.USER_EMAIL, jwt.getClaimAsString("email"));
      if (jwt.hasClaim("email_verified")) {
        headers.set(
            GatewayHeaders.USER_EMAIL_VERIFIED,
            String.valueOf(Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"))));
      }
      String name = jwt.getClaimAsString("name");
      if (StringUtils.hasText(name)) {
        headers.set(GatewayHeaders.USER_NAME, URLEncoder.encode(name, StandardCharsets.UTF_8));
      }
      setIfPresent(headers, GatewayHeaders.USER_PICTURE, jwt.getClaimAsString("picture"));
      log.debug("(rewriteHeaders) identity headers set for uid: {}", jwt.getSubject());
    }
  }

  private static void setIfPresent(HttpHeaders headers, String name, String value) {
    if (StringUtils.hasText(value)) {
      headers.set(name, value);
    }
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }
}
