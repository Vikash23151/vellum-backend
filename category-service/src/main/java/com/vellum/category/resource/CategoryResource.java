package com.vellum.category.resource;

import com.vellum.category.dto.*;
import com.vellum.category.service.CategoryService;
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
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class CategoryResource {

    private final CategoryService categoryService;

    @PostMapping("/api/categories")
    @Operation(summary = "Create a new category (Admin only)")
    @Tag(name = "Categories")
    public ResponseEntity<CategoryResponse> createCategory(
            @Valid @RequestBody CreateCategoryRequest request,
            @RequestHeader("X-User-Role") String userRole) {

        if (!"ADMIN".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        CategoryResponse response = categoryService.createCategory(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/api/categories")
    @Operation(summary = "Get all categories (flat list)")
    @Tag(name = "Categories")
    public ResponseEntity<List<CategoryResponse>> getAllCategories() {
        return ResponseEntity.ok(categoryService.getAllCategories());
    }

    @GetMapping("/api/categories/tree")
    @Operation(summary = "Get categories as hierarchical tree")
    @Tag(name = "Categories")
    public ResponseEntity<List<CategoryResponse>> getCategoryTree() {
        return ResponseEntity.ok(categoryService.getCategoryTree());
    }

    @GetMapping("/api/categories/{slug}")
    @Operation(summary = "Get category by slug")
    @Tag(name = "Categories")
    public ResponseEntity<CategoryResponse> getBySlug(
            @PathVariable String slug) {
        return ResponseEntity.ok(
                categoryService.getCategoryBySlug(slug));
    }

    @GetMapping("/api/categories/{id}/children")
    @Operation(summary = "Get child categories of a parent")
    @Tag(name = "Categories")
    public ResponseEntity<List<CategoryResponse>> getChildren(
            @PathVariable Integer id) {
        return ResponseEntity.ok(
                categoryService.getChildCategories(id));
    }

    @GetMapping("/api/categories/by-ids")
    @Operation(summary = "Get multiple categories by IDs")
    @Tag(name = "Categories")
    public ResponseEntity<List<CategoryResponse>> getByIds(
            @RequestParam List<Integer> ids) {
        return ResponseEntity.ok(
                categoryService.getCategoriesByIds(ids));
    }

    @PutMapping("/api/categories/{id}")
    @Operation(summary = "Update a category (Admin only)")
    @Tag(name = "Categories")
    public ResponseEntity<CategoryResponse> updateCategory(
            @PathVariable Integer id,
            @Valid @RequestBody UpdateCategoryRequest request,
            @RequestHeader("X-User-Role") String userRole) {

        if (!"ADMIN".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.ok(
                categoryService.updateCategory(id, request));
    }

    @DeleteMapping("/api/categories/{id}")
    @Operation(summary = "Delete a category (Admin only)")
    @Tag(name = "Categories")
    public ResponseEntity<Map<String, String>> deleteCategory(
            @PathVariable Integer id,
            @RequestHeader("X-User-Role") String userRole) {

        if (!"ADMIN".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        categoryService.deleteCategory(id);
        return ResponseEntity.ok(
                Map.of("message", "Category deleted"));
    }

    @PostMapping("/api/tags")
    @Operation(summary = "Create a new tag (Admin only)")
    @Tag(name = "Tags")
    public ResponseEntity<TagResponse> createTag(
            @Valid @RequestBody CreateTagRequest request,
            @RequestHeader("X-User-Role") String userRole) {

        if (!"ADMIN".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        TagResponse response = categoryService.createTag(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/api/tags")
    @Operation(summary = "Get all tags (alphabetical)")
    @Tag(name = "Tags")
    public ResponseEntity<List<TagResponse>> getAllTags() {
        return ResponseEntity.ok(categoryService.getAllTags());
    }

    @GetMapping("/api/tags/trending")
    @Operation(summary = "Get trending tags by post count")
    @Tag(name = "Tags")
    public ResponseEntity<List<TagResponse>> getTrending(
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(
                categoryService.getTrendingTags(limit));
    }

    @GetMapping("/api/tags/search")
    @Operation(summary = "Search tags by name (for autocomplete)")
    @Tag(name = "Tags")
    public ResponseEntity<List<TagResponse>> searchTags(
            @RequestParam String q) {
        return ResponseEntity.ok(categoryService.searchTags(q));
    }

    @GetMapping("/api/tags/{slug}")
    @Operation(summary = "Get tag by slug")
    @Tag(name = "Tags")
    public ResponseEntity<TagResponse> getTagBySlug(
            @PathVariable String slug) {
        return ResponseEntity.ok(
                categoryService.getTagBySlug(slug));
    }

    @GetMapping("/api/tags/post/{postId}")
    @Operation(summary = "Get all tags for a post")
    @Tag(name = "Tags")
    public ResponseEntity<List<TagResponse>> getTagsByPost(
            @PathVariable Integer postId) {
        return ResponseEntity.ok(
                categoryService.getTagsByPost(postId));
    }

    @DeleteMapping("/api/tags/{id}")
    @Operation(summary = "Delete a tag (Admin only)")
    @Tag(name = "Tags")
    public ResponseEntity<Map<String, String>> deleteTag(
            @PathVariable Integer id,
            @RequestHeader("X-User-Role") String userRole) {

        if (!"ADMIN".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        categoryService.deleteTag(id);
        return ResponseEntity.ok(Map.of("message", "Tag deleted"));
    }

    @PostMapping("/api/tags/post/{postId}/tag/{tagId}")
    @Operation(summary = "Add a tag to a post (Author/Admin)")
    @Tag(name = "Tags")
    public ResponseEntity<Map<String, String>> addTagToPost(
            @PathVariable Integer postId,
            @PathVariable Integer tagId,
            @RequestHeader("X-User-Role") String userRole) {

        if (!"AUTHOR".equals(userRole) && !"ADMIN".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        categoryService.addTagToPost(postId, tagId);
        return ResponseEntity.ok(
                Map.of("message", "Tag added to post"));
    }

    @DeleteMapping("/api/tags/post/{postId}/tag/{tagId}")
    @Operation(summary = "Remove a tag from a post (Author/Admin)")
    @Tag(name = "Tags")
    public ResponseEntity<Map<String, String>> removeTagFromPost(
            @PathVariable Integer postId,
            @PathVariable Integer tagId,
            @RequestHeader("X-User-Role") String userRole) {

        if (!"AUTHOR".equals(userRole) && !"ADMIN".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        categoryService.removeTagFromPost(postId, tagId);
        return ResponseEntity.ok(
                Map.of("message", "Tag removed from post"));
    }

    @DeleteMapping("/api/tags/post/{postId}")
    @Operation(summary = "Remove all tags from a post")
    @Tag(name = "Tags")
    public ResponseEntity<Map<String, String>> removeAllTagsFromPost(
            @PathVariable Integer postId,
            @RequestHeader("X-User-Role") String userRole) {

        if (!"AUTHOR".equals(userRole) && !"ADMIN".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        categoryService.removeAllTagsFromPost(postId);
        return ResponseEntity.ok(
                Map.of("message", "All tags removed from post"));
    }
}
