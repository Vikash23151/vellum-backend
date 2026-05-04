package com.vellum.post.resource;

import com.vellum.post.dto.*;
import com.vellum.post.service.PostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/*
 * HOW THIS CONTROLLER KNOWS WHO IS MAKING THE REQUEST:
 *
 * Gateway validates JWT and adds headers to every request:
 *   X-User-Id:    42
 *   X-User-Email: john@vellum.com
 *   X-User-Role:  AUTHOR
 *
 * Controller reads these with @RequestHeader.
 * This is the "trust the gateway" pattern.
 *
 * For public endpoints (GET posts): headers may not be present.
 * We use required=false and default values.
 */
@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
@Tag(name = "Posts", description = "Blog post management")
public class PostResource {

    private final PostService postService;

    // ── CREATE POST ───────────────────────────────────────────
    /*
     * POST /api/posts
     * Protected: requires JWT (gateway adds X-User-Id header)
     * Returns 201 Created
     */
    @PostMapping
    @Operation(summary = "Create a new post (Author/Admin)")
    public ResponseEntity<PostResponse> createPost(
            @Valid @RequestBody CreatePostRequest request,
            @RequestHeader("X-User-Id") Integer authorId) {

        log.info("POST /api/posts - authorId: {}", authorId);
        PostResponse response = postService.createPost(request, authorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ── GET PUBLISHED FEED ────────────────────────────────────
    /*
     * GET /api/posts?page=0&size=20
     * Public: no auth required
     * Returns paginated feed of published posts
     */
    @GetMapping
    @Operation(summary = "Get published posts feed (paginated)")
    public ResponseEntity<Page<PostResponse>> getPublishedPosts(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(postService.getPublishedPosts(page, size));
    }

    // ── GET POST BY SLUG ──────────────────────────────────────
    /*
     * GET /api/posts/slug/{slug}
     * Public: no auth required
     * sessionId: used for unique view tracking
     *   - Authenticated user: we use X-User-Id as session
     *   - Anonymous: Angular sends a browser session ID
     */
    @GetMapping("/slug/{slug}")
    @Operation(summary = "Get post by slug (public)")
    public ResponseEntity<PostResponse> getBySlug(
            @PathVariable String slug,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId,
            @RequestHeader(value = "X-User-Id",    required = false) Integer userId) {

        // Use userId as session for authenticated users (more accurate)
        String effectiveSession = userId != null
                ? "user:" + userId
                : sessionId;

        return ResponseEntity.ok(
                postService.getPostBySlug(slug, effectiveSession, userId)
        );
    }

    // ── GET POST BY ID ────────────────────────────────────────
    @GetMapping("/{postId}")
    @Operation(summary = "Get post by ID")
    public ResponseEntity<PostResponse> getById(@PathVariable Integer postId) {
        return ResponseEntity.ok(postService.getPostById(postId));
    }

    // ── GET POSTS BY AUTHOR ───────────────────────────────────
    @GetMapping("/author/{authorId}")
    @Operation(summary = "Get all posts by an author")
    public ResponseEntity<List<PostResponse>> getByAuthor(
            @PathVariable Integer authorId) {
        return ResponseEntity.ok(postService.getPostsByAuthor(authorId));
    }

    // ── SEARCH POSTS ──────────────────────────────────────────
    @GetMapping("/search")
    @Operation(summary = "Search published posts by keyword")
    public ResponseEntity<Page<PostResponse>> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(postService.searchPosts(q, page, size));
    }

    // ── UPDATE POST ───────────────────────────────────────────
    @PutMapping("/{postId}")
    @Operation(summary = "Update a post (Author/Admin)")
    public ResponseEntity<PostResponse> update(
            @PathVariable Integer postId,
            @Valid @RequestBody UpdatePostRequest request,
            @RequestHeader("X-User-Id") Integer userId) {

        return ResponseEntity.ok(postService.updatePost(postId, request, userId));
    }

    // ── PUBLISH POST ──────────────────────────────────────────
    @PutMapping("/{postId}/publish")
    @Operation(summary = "Publish a draft post")
    public ResponseEntity<PostResponse> publish(
            @PathVariable Integer postId,
            @RequestHeader("X-User-Id") Integer userId) {

        return ResponseEntity.ok(postService.publishPost(postId, userId));
    }

    // ── UNPUBLISH POST ────────────────────────────────────────
    @PutMapping("/{postId}/unpublish")
    @Operation(summary = "Unpublish a post")
    public ResponseEntity<PostResponse> unpublish(
            @PathVariable Integer postId,
            @RequestHeader("X-User-Id") Integer userId) {

        return ResponseEntity.ok(postService.unpublishPost(postId, userId));
    }

    // ── DELETE POST ───────────────────────────────────────────
    @DeleteMapping("/{postId}")
    @Operation(summary = "Delete a post")
    public ResponseEntity<Map<String, String>> delete(
            @PathVariable Integer postId,
            @RequestHeader("X-User-Id") Integer userId) {

        postService.deletePost(postId, userId);
        return ResponseEntity.ok(Map.of("message", "Post deleted successfully"));
    }

    // ── LIKE POST ─────────────────────────────────────────────
    @PostMapping("/{postId}/like")
    @Operation(summary = "Like a post")
    public ResponseEntity<Map<String, String>> like(
            @PathVariable Integer postId,
            @RequestHeader("X-User-Id") Integer userId) {

        postService.likePost(postId, userId);
        return ResponseEntity.ok(Map.of("message", "Post liked"));
    }

    // ── UNLIKE POST ───────────────────────────────────────────
    @DeleteMapping("/{postId}/like")
    @Operation(summary = "Unlike a post")
    public ResponseEntity<Map<String, String>> unlike(
            @PathVariable Integer postId,
            @RequestHeader("X-User-Id") Integer userId) {

        postService.unlikePost(postId, userId);
        return ResponseEntity.ok(Map.of("message", "Post unliked"));
    }

    // ── IS LIKED BY USER ──────────────────────────────────────
    @GetMapping("/{postId}/liked")
    @Operation(summary = "Check if current user liked this post")
    public ResponseEntity<Map<String, Boolean>> isLiked(
            @PathVariable Integer postId,
            @RequestHeader(value = "X-User-Id", required = false) Integer userId) {

        boolean liked = userId != null
                && postService.isLikedByUser(postId, userId);
        return ResponseEntity.ok(Map.of("liked", liked));
    }

    // ── FEATURE POST (ADMIN) ──────────────────────────────────
    @PutMapping("/{postId}/feature")
    @Operation(summary = "Feature/unfeature a post (Admin only)")
    public ResponseEntity<Map<String, String>> feature(
            @PathVariable Integer postId,
            @RequestBody Map<String, Boolean> body,
            @RequestHeader("X-User-Role") String userRole) {

        if (!"ADMIN".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Admin access required"));
        }

        postService.featurePost(postId, body.getOrDefault("featured", true));
        return ResponseEntity.ok(Map.of("message", "Post feature status updated"));
    }

    // ── GET STATS ─────────────────────────────────────────────
    @GetMapping("/stats/count")
    @Operation(summary = "Get total post count")
    public ResponseEntity<Map<String, Long>> getCount() {
        return ResponseEntity.ok(Map.of("count", postService.getPostCount()));
    }

    // ── MOST VIEWED POSTS ─────────────────────────────────────
    @GetMapping("/most-viewed")
    @Operation(summary = "Get most viewed posts")
    public ResponseEntity<List<PostResponse>> getMostViewed(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(postService.getMostViewedPosts(limit));
    }
}