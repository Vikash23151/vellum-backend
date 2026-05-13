package com.vellum.newsletter.resource;

import com.vellum.newsletter.dto.*;
import com.vellum.newsletter.service.NewsletterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/newsletter")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
@Tag(name = "Newsletter",
        description = "Email subscription and campaign management")
public class NewsletterResource {

    private final NewsletterService newsletterService;

    // SUBSCRIBE
    /*
     * POST /api/newsletter/subscribe
     * Public: no auth required.
     * Optional auth: if logged in, userId is extracted from header.
     */
    @PostMapping("/subscribe")
    @Operation(summary = "Subscribe to newsletter (starts double opt-in)")
    public ResponseEntity<Map<String, String>> subscribe(
            @Valid @RequestBody SubscribeRequest request,
            @RequestHeader(value = "X-User-Id",
                    required = false) Integer userId) {

        // If authenticated, link subscription to user account
        if (userId != null) {
            request.setUserId(userId);
        }

        newsletterService.subscribe(request);

        return ResponseEntity.ok(Map.of(
                "message",
                "Check your email for a confirmation link. " +
                        "The link expires in 24 hours."));
    }

    // CONFIRM SUBSCRIPTION
    /*
     * GET /api/newsletter/confirm/{token}
     * Public: no auth (user clicks from email).
     * Angular routes to a confirmation success/error page.
     */
    @GetMapping("/confirm/{token}")
    @Operation(summary = "Confirm email subscription via token")
    public ResponseEntity<Map<String, Object>> confirm(
            @PathVariable String token) {

        SubscriberResponse subscriber =
                newsletterService.confirmSubscription(token);

        return ResponseEntity.ok(Map.of(
                "message", "Your subscription is confirmed! " +
                        "Welcome to Vellum!",
                "email",   subscriber.getEmail(),
                "status",  subscriber.getStatus()));
    }

    // UNSUBSCRIBE
    /*
     * GET /api/newsletter/unsubscribe/{token}
     * Public: no auth (user clicks from email).
     * Returns 200 with message — Angular shows confirmation page.
     */
    @GetMapping("/unsubscribe/{token}")
    @Operation(summary = "Unsubscribe using token from email")
    public ResponseEntity<Map<String, String>> unsubscribe(
            @PathVariable String token) {

        newsletterService.unsubscribe(token);

        return ResponseEntity.ok(Map.of(
                "message",
                "You've been successfully unsubscribed. " +
                        "You won't receive any more emails from us."));
    }

    // UPDATE PREFERENCES
    @PutMapping("/preferences/{subscriberId}")
    @Operation(summary = "Update subscription preferences")
    public ResponseEntity<SubscriberResponse> updatePreferences(
            @PathVariable Integer subscriberId,
            @RequestBody Map<String, String> body) {

        String preferences = body.getOrDefault("preferences", "");
        return ResponseEntity.ok(
                newsletterService.updatePreferences(
                        subscriberId, preferences));
    }

    // ADMIN ENDPOINTS

    // GET ALL SUBSCRIBERS
    @GetMapping("/admin/subscribers")
    @Operation(summary = "Get all subscribers (Admin only)")
    public ResponseEntity<List<SubscriberResponse>> getAllSubscribers(
            @RequestHeader("X-User-Role") String userRole) {

        if (!"ADMIN".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.ok(
                newsletterService.getAllSubscribers());
    }

    // GET ACTIVE SUBSCRIBERS
    @GetMapping("/admin/subscribers/active")
    @Operation(summary = "Get active subscribers (Admin only)")
    public ResponseEntity<List<SubscriberResponse>> getActiveSubscribers(
            @RequestHeader("X-User-Role") String userRole) {

        if (!"ADMIN".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.ok(
                newsletterService.getActiveSubscribers());
    }

    // SEND CAMPAIGN
    /*
     * POST /api/newsletter/admin/send
     * Starts async campaign — returns immediately.
     * Emails sent in background.
     */
    @PostMapping("/admin/send")
    @Operation(summary = "Send newsletter campaign (Admin only)")
    public ResponseEntity<Map<String, String>> sendNewsletter(
            @Valid @RequestBody SendNewsletterRequest request,
            @RequestHeader("X-User-Role") String userRole) {

        if (!"ADMIN".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        log.info("Admin triggered newsletter campaign: {}",
                request.getSubject());

        // Async — returns immediately
        newsletterService.sendNewsletter(request);

        return ResponseEntity.ok(Map.of(
                "message",
                "Newsletter campaign started. " +
                        "Emails are being sent in the background."));
    }

    // GET STATS
    @GetMapping("/admin/stats")
    @Operation(summary = "Get subscriber statistics (Admin only)")
    public ResponseEntity<Map<String, Long>> getStats(
            @RequestHeader("X-User-Role") String userRole) {

        if (!"ADMIN".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.ok(newsletterService.getStats());
    }

    // GET BY EMAIL
    @GetMapping("/admin/subscriber")
    @Operation(summary = "Get subscriber by email (Admin only)")
    public ResponseEntity<SubscriberResponse> getByEmail(
            @RequestParam String email,
            @RequestHeader("X-User-Role") String userRole) {

        if (!"ADMIN".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.ok(
                newsletterService.getSubscriberByEmail(email));
    }
}