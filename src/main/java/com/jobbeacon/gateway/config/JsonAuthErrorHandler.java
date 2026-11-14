package com.jobbeacon.gateway.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** Writes 401/403 as JSON compatible with backend {@code ResponseGeneral} (snake_case). */
@Slf4j
@Component
@RequiredArgsConstructor
public class JsonAuthErrorHandler implements ServerAuthenticationEntryPoint, ServerAccessDeniedHandler {

  private final ObjectMapper objectMapper;

  @Override
  public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
    log.debug("(commence) unauthorized request: {}", exchange.getRequest().getPath());
    return write(exchange.getResponse(), HttpStatus.UNAUTHORIZED, "unauthorized");
  }

  @Override
  public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException denied) {
    log.debug("(handle) forbidden request: {}", exchange.getRequest().getPath());
    return write(exchange.getResponse(), HttpStatus.FORBIDDEN, "forbidden");
  }

  private Mono<Void> write(ServerHttpResponse response, HttpStatus status, String message) {
    if (response.isCommitted()) {
      return Mono.empty();
    }
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("status", status.value());
    body.put("message", message);
    body.put("data", null);
    body.put("timestamp", LocalDate.now().toString());
    byte[] bytes;
    try {
      bytes = objectMapper.writeValueAsBytes(body);
    } catch (JsonProcessingException e) {
      return Mono.error(e);
    }
    response.setStatusCode(status);
    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
    return response.writeWith(
        Mono.fromSupplier(() -> response.bufferFactory().wrap(bytes)));
  }
}
