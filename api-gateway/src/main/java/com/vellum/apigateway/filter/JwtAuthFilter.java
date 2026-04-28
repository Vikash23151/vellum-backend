package com.vellum.apigateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.Key;

/*
 * extends AbstractGatewayFilterFactory<Config>:
 * This is the Spring Cloud Gateway way to create named filters.
 * "name: JwtAuthFilter" in application.yml maps to this class.
 * The <Config> generic allows per-route configuration.
 */
@Component
@Slf4j
public class JwtAuthFilter extends
        AbstractGatewayFilterFactory<JwtAuthFilter.Config> {

    @Value("${jwt.secret}")
    private String jwtSecret;

    public JwtAuthFilter() {
        super(Config.class);
    }

    /*
     * apply() is called ONCE per route at startup to create the filter.
     * The returned GatewayFilter lambda runs on EVERY request to that route.
     *
     * exchange = the current HTTP request+response pair
     * chain    = the next filter in the chain (or the actual route call)
     */
    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {

            ServerHttpRequest request = exchange.getRequest();
            String path = request.getURI().getPath();
            log.debug("JwtAuthFilter processing: {}", path);

            // Step 1: Extract Authorization header
            String authHeader = request.getHeaders()
                    .getFirst(HttpHeaders.AUTHORIZATION);

            if (authHeader == null || authHeader.isBlank()) {
                log.warn("No Authorization header | Path: {}", path);
                return sendErrorResponse(
                        exchange,
                        HttpStatus.UNAUTHORIZED,
                        "Authorization header is missing"
                );
            }

            // Step 2: Check Bearer format
            // Token must start with "Bearer "
            if (!authHeader.startsWith("Bearer ")) {
                log.warn("Invalid Authorization format | Path: {}", path);
                return sendErrorResponse(
                        exchange,
                        HttpStatus.UNAUTHORIZED,
                        "Authorization header must start with 'Bearer '"
                );
            }

            // Step 3: Extract token string
            // "Bearer eyJhbGc..." → "eyJhbGc..."
            String token = authHeader.substring(7);

            if (token.isBlank()) {
                return sendErrorResponse(
                        exchange,
                        HttpStatus.UNAUTHORIZED,
                        "JWT token is empty"
                );
            }

            // Step 4: Validate and parse token
            Claims claims;
            try {
                claims = parseToken(token);
            } catch (ExpiredJwtException e) {
                log.warn("Token expired | Path: {}", path);
                return sendErrorResponse(
                        exchange,
                        HttpStatus.UNAUTHORIZED,
                        "JWT token has expired. Please login again."
                );
            } catch (JwtException e) {
                log.warn("Invalid token: {} | Path: {}", e.getMessage(), path);
                return sendErrorResponse(
                        exchange,
                        HttpStatus.UNAUTHORIZED,
                        "JWT token is invalid"
                );
            } catch (Exception e) {
                log.error("Token processing error: {}", e.getMessage());
                return sendErrorResponse(
                        exchange,
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Token processing failed"
                );
            }

            // Step 5: Extract user info from claims
            String userEmail = claims.getSubject();
            String userRole  = claims.get("role",   String.class);
            String userId    = claims.get("userId", String.class);

            log.debug("Token valid | Email: {} | Role: {} | UserId: {}",
                    userEmail, userRole, userId);

            // Step 6: Add user info to request headers
            /*
             * Add headers instead of passing token forward
             *
             * Microservices need to know WHO is making the request.
             * Add X-User-* headers → services trust gateway's validation
             *
             * - No repeated JWT validation in each service
             * - Services become simpler (just read a header)
             * - Gateway is the single point of trust
             *
             * Services MUST NOT be publicly accessible (firewall them).
             * Only gateway should reach them. Then X-User-* headers are safe.
             */
            ServerHttpRequest modifiedRequest = request.mutate()
                    .header("X-User-Email",  userEmail != null ? userEmail : "")
                    .header("X-User-Role",   userRole  != null ? userRole  : "")
                    .header("X-User-Id",     userId    != null ? userId    : "")
                    // Remove original Authorization so services don't process it
                    .build();

            ServerWebExchange modifiedExchange = exchange.mutate()
                    .request(modifiedRequest)
                    .build();

            // Step 7: Forward to microservice
            return chain.filter(modifiedExchange);
        };
    }

    /*
     * Parses JWT token and returns the claims (payload).
     * Throws JwtException if signature is invalid or token is malformed.
     * Throws ExpiredJwtException if token's expiry date has passed.
     */
    private Claims parseToken(String token) {
        Key signingKey = Keys.hmacShaKeyFor(
                jwtSecret.getBytes(StandardCharsets.UTF_8)
        );

        return Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /*
     * Sends a JSON error response back to Angular.
     * The microservice is NEVER called when this method runs.
     *
     * WHY return Mono<Void>?
     * WebFlux is reactive. Mono<Void> means:
     * "I will eventually complete this response asynchronously."
     * It doesn't block a thread waiting.
     */
    private Mono<Void> sendErrorResponse(ServerWebExchange exchange,
                                         HttpStatus status,
                                         String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        // Build JSON error body
        String body = String.format(
                "{\"error\": \"%s\", \"status\": %d, \"path\": \"%s\"}",
                message,
                status.value(),
                exchange.getRequest().getURI().getPath()
        );

        DataBuffer buffer = response.bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));

        return response.writeWith(Mono.just(buffer));
    }

    /*
     * Config class for per-route configuration.
     * Currently empty but can be extended.
     *
     * Example extension: add required roles per route
     *   config.setRequiredRole("ADMIN")
     * Then filter checks if user has ADMIN role.
     */
    public static class Config {
        private String requiredRole; // optional: enforce role at gateway level

        public String getRequiredRole() { return requiredRole; }
        public void setRequiredRole(String requiredRole) {
            this.requiredRole = requiredRole;
        }
    }
}