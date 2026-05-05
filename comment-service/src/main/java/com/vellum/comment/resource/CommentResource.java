package com.vellum.comment.resource;

import com.vellum.comment.dto.*;
import com.vellum.comment.service.CommentService;
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
@RequestMapping("/api/comments")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
@Tag(name = "Comments", description = "Threaded comment management for blog posts")
public class CommentResource {

    private final CommentService commentService;

    @PostMapping
    @Operation(summary = "Add a comment or reply to a post")
    public ResponseEntity<CommentResponse> addComment(
            @Valid @RequestBody AddCommentRequest request,
            @RequestHeader("X-User-Id") Integer authorId) {
        CommentResponse response = commentService.addComment(request, authorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/post/{postId}")
    @Operation(summary = "Get all comments for a post (threaded)")
    public ResponseEntity<List<CommentResponse>> getByPost(
            @PathVariable Integer postId,
            @RequestHeader(value = "X-User-Id", required = false) Integer userId) {
        return ResponseEntity.ok(commentService.getCommentsByPost(postId, userId));
    }

    @GetMapping("/{commentId}")
    @Operation(summary = "Get a single comment by ID")
    public ResponseEntity<CommentResponse> getById(@PathVariable Integer commentId) {
        return ResponseEntity.ok(commentService.getCommentById(commentId));
    }

    @GetMapping("/{commentId}/replies")
    @Operation(summary = "Get all replies to a comment")
    public ResponseEntity<List<CommentResponse>> getReplies(
            @PathVariable Integer commentId,
            @RequestHeader(value = "X-User-Id", required = false) Integer userId) {
        return ResponseEntity.ok(commentService.getReplies(commentId, userId));
    }

    @GetMapping("/post/{postId}/moderate")
    @Operation(summary = "Get all comments for moderation (Author/Admin)")
    public ResponseEntity<List<CommentResponse>> getForModeration(
            @PathVariable Integer postId,
            @RequestHeader("X-User-Role") String userRole) {

        if (!"AUTHOR".equals(userRole) && !"ADMIN".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.ok(commentService.getCommentsForModeration(postId));
    }

    @PutMapping("/{commentId}")
    @Operation(summary = "Edit your own comment (within time window)")
    public ResponseEntity<CommentResponse> update(
            @PathVariable Integer commentId,
            @Valid @RequestBody UpdateCommentRequest request,
            @RequestHeader("X-User-Id") Integer userId) {
        return ResponseEntity.ok(commentService.updateComment(commentId, request, userId));
    }

    @DeleteMapping("/{commentId}")
    @Operation(summary = "Delete a comment (soft delete)")
    public ResponseEntity<Map<String, String>> delete(
            @PathVariable Integer commentId,
            @RequestHeader("X-User-Id") Integer userId,
            @RequestHeader("X-User-Role") String userRole) {
        commentService.deleteComment(commentId, userId, userRole);
        return ResponseEntity.ok(Map.of("message", "Comment deleted successfully"));
    }

    @PutMapping("/{commentId}/approve")
    @Operation(summary = "Approve a pending comment (Author/Admin)")
    public ResponseEntity<CommentResponse> approve(
            @PathVariable Integer commentId,
            @RequestHeader("X-User-Id") Integer userId,
            @RequestHeader("X-User-Role") String userRole) {
        return ResponseEntity.ok(commentService.approveComment(commentId, userId, userRole));
    }

    @PutMapping("/{commentId}/reject")
    @Operation(summary = "Reject a pending comment (Author/Admin)")
    public ResponseEntity<CommentResponse> reject(
            @PathVariable Integer commentId,
            @RequestHeader("X-User-Id") Integer userId,
            @RequestHeader("X-User-Role") String userRole) {
        return ResponseEntity.ok(commentService.rejectComment(commentId, userId, userRole));
    }

    @PostMapping("/{commentId}/like")
    @Operation(summary = "Like a comment")
    public ResponseEntity<Map<String, String>> like(
            @PathVariable Integer commentId,
            @RequestHeader("X-User-Id") Integer userId) {
        commentService.likeComment(commentId, userId);
        return ResponseEntity.ok(Map.of("message", "Comment liked"));
    }

    @DeleteMapping("/{commentId}/like")
    @Operation(summary = "Unlike a comment")
    public ResponseEntity<Map<String, String>> unlike(
            @PathVariable Integer commentId,
            @RequestHeader("X-User-Id") Integer userId) {
        commentService.unlikeComment(commentId, userId);
        return ResponseEntity.ok(Map.of("message", "Comment unliked"));
    }

    @GetMapping("/post/{postId}/count")
    @Operation(summary = "Get approved comment count for a post")
    public ResponseEntity<Map<String, Long>> getCount(@PathVariable Integer postId) {
        return ResponseEntity.ok(Map.of("count", commentService.getCommentCount(postId)));
    }
}
