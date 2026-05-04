package com.vellum.apigateway.filter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthFilter Tests")
class JwtAuthFilterTest {

    @InjectMocks
    private JwtAuthFilter jwtAuthFilter;

    private GatewayFilterChain mockChain;

    private static final String SECRET =
            "vellumSuperSecretKeyThatIsAtLeast256BitsLong1234567890";

    private static final String TEST_EMAIL  = "test@vellum.com";
    private static final String TEST_ROLE   = "AUTHOR";
    private static final Integer TEST_USER_ID = 42;

    @BeforeEach
    void setUp() {
        mockChain = mock(GatewayFilterChain.class);

        ReflectionTestUtils.setField(jwtAuthFilter, "jwtSecret", SECRET);
    }

    // Helper: generate a valid test JWT

    private String generateValidToken() {
        Key key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

        return Jwts.builder()
                .setSubject(TEST_EMAIL)
                .claim("role",   TEST_ROLE)
                .claim("userId", TEST_USER_ID.toString())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 86400000L))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    private String generateExpiredToken() {
        Key key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

        return Jwts.builder()
                .setSubject(TEST_EMAIL)
                .claim("role",   TEST_ROLE)
                .claim("userId", TEST_USER_ID.toString())
                .setIssuedAt(new Date(System.currentTimeMillis() - 86400000L * 2))
                .setExpiration(new Date(System.currentTimeMillis() - 86400000L)) // expired yesterday
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    // TEST 1: Valid token → request passes through

    @Test
    @DisplayName("Valid JWT token should pass through filter and add user headers")
    void whenValidToken_thenRequestPassesThrough() {
        // ARRANGE
        String validToken = generateValidToken();

        /*
         * MockServerHttpRequest: creates a fake HTTP request.
         * We use it to simulate what browser sends to gateway.
         * .header() adds headers to the fake request.
         */
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/posts")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                .build();

        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(mockChain.filter(any())).thenReturn(Mono.empty());

        // Get the actual GatewayFilter from our filter factory
        var gatewayFilter = jwtAuthFilter.apply(new JwtAuthFilter.Config());


        StepVerifier.create(gatewayFilter.filter(exchange, mockChain))
                .verifyComplete();

        // Verify chain.filter() was called (request passed through)
        verify(mockChain, times(1)).filter(any());

        // Verify response is NOT 401 (no error set)
        assertThat(exchange.getResponse().getStatusCode())
                .isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // TEST 2: Missing Authorization header → 401

    @Test
    @DisplayName("Missing Authorization header should return 401")
    void whenNoAuthHeader_thenReturn401() {
        // ARRANGE: request with NO Authorization header
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/posts")
                .build();  // no Authorization header

        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        var gatewayFilter = jwtAuthFilter.apply(new JwtAuthFilter.Config());

        // ACT + ASSERT
        StepVerifier.create(gatewayFilter.filter(exchange, mockChain))
                .verifyComplete();

        // Chain should NOT be called (request blocked at filter)
        verify(mockChain, never()).filter(any());

        // Response should be 401
        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // TEST 3: Invalid token format (no "Bearer " prefix) → 401

    @Test
    @DisplayName("Authorization header without 'Bearer ' prefix should return 401")
    void whenInvalidAuthFormat_thenReturn401() {
        // ARRANGE: Authorization header without "Bearer " prefix
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/posts")
                .header(HttpHeaders.AUTHORIZATION, "Basic sometoken")
                .build();

        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        var gatewayFilter = jwtAuthFilter.apply(new JwtAuthFilter.Config());

        // ACT + ASSERT
        StepVerifier.create(gatewayFilter.filter(exchange, mockChain))
                .verifyComplete();

        verify(mockChain, never()).filter(any());
        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }


    // TEST 4: Expired token → 401

    @Test
    @DisplayName("Expired JWT token should return 401")
    void whenExpiredToken_thenReturn401() {
        // ARRANGE: use our helper to generate an already-expired token
        String expiredToken = generateExpiredToken();

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/posts")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken)
                .build();

        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        var gatewayFilter = jwtAuthFilter.apply(new JwtAuthFilter.Config());

        // ACT + ASSERT
        StepVerifier.create(gatewayFilter.filter(exchange, mockChain))
                .verifyComplete();

        verify(mockChain, never()).filter(any());
        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // TEST 5: Tampered token (wrong signature) → 401

    @Test
    @DisplayName("Tampered JWT token should return 401")
    void whenTamperedToken_thenReturn401() {
        // ARRANGE: Take a valid token and tamper with its payload
        String validToken = generateValidToken();

        // Split JWT into header.payload.signature
        String[] parts = validToken.split("\\.");

        // Modify the payload (middle part) by appending extra chars
        // This breaks the signature → invalid token
        String tamperedToken = parts[0] + "." + parts[1] + "TAMPERED" + "." + parts[2];

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/posts")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tamperedToken)
                .build();

        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        var gatewayFilter = jwtAuthFilter.apply(new JwtAuthFilter.Config());

        // ACT + ASSERT
        StepVerifier.create(gatewayFilter.filter(exchange, mockChain))
                .verifyComplete();

        verify(mockChain, never()).filter(any());
        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // TEST 6: Empty Bearer token → 401

    @Test
    @DisplayName("Empty Bearer token should return 401")
    void whenEmptyToken_thenReturn401() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/posts")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ")  // empty after "Bearer "
                .build();

        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        var gatewayFilter = jwtAuthFilter.apply(new JwtAuthFilter.Config());

        StepVerifier.create(gatewayFilter.filter(exchange, mockChain))
                .verifyComplete();

        verify(mockChain, never()).filter(any());
        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // TEST 7: Completely random string as token → 401

    @Test
    @DisplayName("Random string as JWT should return 401")
    void whenRandomStringToken_thenReturn401() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/posts")
                .header(HttpHeaders.AUTHORIZATION, "Bearer thisisnotatoken12345")
                .build();

        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        var gatewayFilter = jwtAuthFilter.apply(new JwtAuthFilter.Config());

        StepVerifier.create(gatewayFilter.filter(exchange, mockChain))
                .verifyComplete();

        verify(mockChain, never()).filter(any());
        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}