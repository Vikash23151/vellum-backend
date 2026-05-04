package com.vellum.apigateway.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("Gateway Configuration Tests")
class GatewayConfigTest {

    @Autowired
    private RouteLocator routeLocator;

    @Test
    @DisplayName("All required routes should be configured")
    void allRoutesConfigured() {

        var routes = routeLocator.getRoutes().collectList().block();

        assertThat(routes).isNotNull();
        assertThat(routes).isNotEmpty();

        // Verify specific route IDs exist
        var routeIds = routes.stream()
                .map(r -> r.getId())
                .toList();

        assertThat(routeIds).contains("auth-service-public");
        assertThat(routeIds).contains("auth-service-protected");
        assertThat(routeIds).contains("post-service-public-read");
        assertThat(routeIds).contains("post-service-protected-write");
        assertThat(routeIds).contains("comment-service-public");
        assertThat(routeIds).contains("comment-service-protected");
        assertThat(routeIds).contains("category-service-public");
        assertThat(routeIds).contains("category-service-protected");
        assertThat(routeIds).contains("media-service-protected");
        assertThat(routeIds).contains("newsletter-service-public");
        assertThat(routeIds).contains("newsletter-service-protected");
        assertThat(routeIds).contains("notification-service-protected");
    }

    @Test
    @DisplayName("Should have at least 10 routes configured")
    void shouldHaveMinimumRoutes() {
        var routes = routeLocator.getRoutes().collectList().block();
        assertThat(routes).hasSizeGreaterThanOrEqualTo(10);
    }
}