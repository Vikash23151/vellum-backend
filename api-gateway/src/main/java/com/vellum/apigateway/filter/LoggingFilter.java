package com.vellum.apigateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/*
 * GlobalFilter (this class):
 *   - Applied to EVERY SINGLE request automatically
 *   - No need to declare it in routes
 *   - Perfect for logging, metrics, request IDs
 *
 * implements Ordered:
 *   getOrder() controls execution order among GlobalFilters.
 *   Lower number = runs first.
 *   We use -1 so logging runs before everything else.
 *   This means we log the request BEFORE JWT validation runs.
 */
@Component
@Slf4j
public class LoggingFilter implements GlobalFilter, Ordered {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange,
                             GatewayFilterChain chain) {

        ServerHttpRequest request = exchange.getRequest();
        String requestId = generateRequestId();
        long startTime   = System.currentTimeMillis();

        // Add request ID to request so microservices can log it too
        // This helps trace a single request across multiple services
        ServerHttpRequest mutatedRequest = request.mutate()
                .header("X-Request-Id", requestId)
                .build();

        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(mutatedRequest)
                .build();

        // Log incoming request
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("   INCOMING REQUEST");
        log.info("   ID      : {}", requestId);
        log.info("   Time    : {}", LocalDateTime.now().format(FORMATTER));
        log.info("   Method  : {}", request.getMethod());
        log.info("   Path    : {}", request.getURI().getPath());
        log.info("   Query   : {}", request.getURI().getQuery());
        log.info("   From    : {}", request.getRemoteAddress());
        log.info("   Auth    : {}",
                request.getHeaders().containsKey("Authorization")
                        ? "Bearer [PRESENT]"
                        : "None"
        );

        /*
         * chain.filter() is the reactive way of saying "continue".
         * .then(Mono.fromRunnable(...)) means "after response comes back, run this".
         * This is how we log BOTH request AND response in reactive code.
         *
         */
        return chain.filter(mutatedExchange)
                .then(Mono.fromRunnable(() -> {
                    long duration = System.currentTimeMillis() - startTime;
                    int statusCode = mutatedExchange.getResponse()
                            .getStatusCode() != null
                            ? mutatedExchange.getResponse().getStatusCode().value()
                            : 0;

                    // Color-code by status
                    String statusEmoji = statusCode < 300 ? "✅"
                            : statusCode < 400 ? "🔄"
                              : statusCode < 500 ? "⚠️"
                                : "❌";

                    log.info("   RESPONSE");
                    log.info("   ID      : {}", requestId);
                    log.info("   Status  : {} {}", statusEmoji, statusCode);
                    log.info("   Duration: {}ms", duration);
                    log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                }));
    }

    @Override
    public int getOrder() {
        return -1; // Run first among all GlobalFilters
    }

    private String generateRequestId() {
        // Simple 8-char ID for log correlation
        return Long.toHexString(System.nanoTime()).substring(0, 8).toUpperCase();
    }
}