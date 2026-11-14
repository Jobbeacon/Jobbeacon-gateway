package com.jobbeacon.gateway.config;

import com.jobbeacon.gateway.properties.AppProperties;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.ServerAuthenticationEntryPointFailureHandler;
import org.springframework.security.web.server.authentication.ServerAuthenticationFailureHandler;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.util.matcher.OrServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  static final String FIREBASE_JWK_SET_URI =
      "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";
  static final String FIREBASE_ISSUER_PREFIX = "https://securetoken.google.com/";
  static final String ROLE_CLAIM = "role";
  static final String DEFAULT_ROLE = "USER";

  private final AppProperties appProperties;
  private final JsonAuthErrorHandler authErrorHandler;

  @Bean
  public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
    ServerWebExchangeMatcher publicEndpoints = publicEndpointMatcher();
    return http.cors(Customizer.withDefaults())
        .csrf(ServerHttpSecurity.CsrfSpec::disable)
        .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
        .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
        .logout(ServerHttpSecurity.LogoutSpec::disable)
        .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
        .authorizeExchange(
            exchanges ->
                exchanges
                    .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .matchers(publicEndpoints).permitAll()
                    .anyExchange().authenticated())
        .oauth2ResourceServer(
            oauth2 ->
                oauth2
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(firebaseAuthenticationConverter()))
                    .authenticationEntryPoint(authErrorHandler)
                    .authenticationFailureHandler(anonymousOnPublicEndpoints(publicEndpoints))
                    .accessDeniedHandler(authErrorHandler))
        .exceptionHandling(
            ex -> ex.authenticationEntryPoint(authErrorHandler).accessDeniedHandler(authErrorHandler))
        .build();
  }

  /** Matches public paths (any method) and GET-only public paths from {@link AppProperties}. */
  ServerWebExchangeMatcher publicEndpointMatcher() {
    List<ServerWebExchangeMatcher> matchers = new ArrayList<>();
    appProperties.publicPaths().forEach(
        path -> matchers.add(new PathPatternParserServerWebExchangeMatcher(path)));
    appProperties.publicGetPaths().forEach(
        path -> matchers.add(new PathPatternParserServerWebExchangeMatcher(path, HttpMethod.GET)));
    return new OrServerWebExchangeMatcher(matchers);
  }

  /**
   * An invalid or expired token on a public endpoint is treated as anonymous (same as the backend's
   * FirebaseTokenFilter) instead of 401; on protected endpoints the JSON 401 is returned.
   */
  ServerAuthenticationFailureHandler anonymousOnPublicEndpoints(ServerWebExchangeMatcher publicEndpoints) {
    ServerAuthenticationFailureHandler reject =
        new ServerAuthenticationEntryPointFailureHandler(authErrorHandler);
    return (webFilterExchange, exception) ->
        publicEndpoints
            .matches(webFilterExchange.getExchange())
            .flatMap(
                match ->
                    match.isMatch()
                        ? webFilterExchange.getChain().filter(webFilterExchange.getExchange())
                        : reject.onAuthenticationFailure(webFilterExchange, exception));
  }

  @Bean
  public ReactiveJwtDecoder reactiveJwtDecoder() {
    String projectId = appProperties.firebase().projectId();
    NimbusReactiveJwtDecoder decoder =
        NimbusReactiveJwtDecoder.withJwkSetUri(FIREBASE_JWK_SET_URI).build();
    OAuth2TokenValidator<Jwt> audience =
        token ->
            token.getAudience() != null && token.getAudience().contains(projectId)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "The required audience is missing", null));
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(FIREBASE_ISSUER_PREFIX + projectId), audience));
    return decoder;
  }

  static Converter<Jwt, Mono<AbstractAuthenticationToken>> firebaseAuthenticationConverter() {
    return jwt ->
        Mono.just(new JwtAuthenticationToken(jwt, authorities(jwt), jwt.getSubject()));
  }

  static List<GrantedAuthority> authorities(Jwt jwt) {
    Object role = jwt.getClaims().get(ROLE_CLAIM);
    List<String> roles =
        role instanceof String single
            ? List.of(single)
            : role instanceof Collection<?> many
                ? many.stream().map(String::valueOf).toList()
                : List.of();
    List<GrantedAuthority> authorities =
        roles.stream()
            .filter(StringUtils::hasText)
            .map(r -> (GrantedAuthority) new SimpleGrantedAuthority(
                "ROLE_" + r.trim().toUpperCase(Locale.ROOT)))
            .toList();
    return authorities.isEmpty()
        ? List.of(new SimpleGrantedAuthority("ROLE_" + DEFAULT_ROLE))
        : authorities;
  }
}
