package com.vellum.media.resource;

import com.vellum.media.dto.*;
import com.vellum.media.service.MediaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
@Tag(name = "Media", description = "File upload and management")
public class MediaResource {

    private final MediaService mediaService;

    // UPLOAD FILE
    @PostMapping(
            value = "/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @Operation(summary = "Upload a media file to S3")
    public ResponseEntity<MediaResponse> uploadMedia(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "altText",
                    required = false) String altText,
            @RequestHeader("X-User-Id") Integer uploaderId) {

        log.info("POST /api/media/upload - file={} uploader={}",
                file.getOriginalFilename(), uploaderId);

        try {
            MediaResponse response =
                    mediaService.uploadMedia(file, uploaderId, altText);
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(response);

        } catch (IOException e) {
            log.error("Upload failed: {}", e.getMessage());
            throw new RuntimeException("File upload failed: " +
                    e.getMessage());
        }
    }

    // GET MY MEDIA LIBRARY
    @GetMapping("/my")
    @Operation(summary = "Get my uploaded media files")
    public ResponseEntity<List<MediaResponse>> getMyMedia(
            @RequestHeader("X-User-Id") Integer userId) {

        return ResponseEntity.ok(
                mediaService.getMediaByUploader(userId));
    }

    // GET BY ID
    @GetMapping("/{mediaId}")
    @Operation(summary = "Get media file by ID")
    public ResponseEntity<MediaResponse> getById(
            @PathVariable Integer mediaId) {

        return ResponseEntity.ok(mediaService.getMediaById(mediaId));
    }

    // GET BY POST
    @GetMapping("/post/{postId}")
    @Operation(summary = "Get all media files for a post")
    public ResponseEntity<List<MediaResponse>> getByPost(
            @PathVariable Integer postId) {

        return ResponseEntity.ok(
                mediaService.getMediaByPost(postId));
    }

    // UPDATE ALT TEXT
    @PutMapping("/{mediaId}/alt-text")
    @Operation(summary = "Update alt text for accessibility")
    public ResponseEntity<MediaResponse> updateAltText(
            @PathVariable Integer mediaId,
            @Valid @RequestBody UpdateAltTextRequest request,
            @RequestHeader("X-User-Id") Integer userId) {

        return ResponseEntity.ok(
                mediaService.updateAltText(mediaId, request, userId));
    }

    // LINK TO POST
    @PostMapping("/{mediaId}/link/{postId}")
    @Operation(summary = "Link media file to a post")
    public ResponseEntity<Map<String, String>> linkToPost(
            @PathVariable Integer mediaId,
            @PathVariable Integer postId,
            @RequestHeader("X-User-Id") Integer userId) {

        mediaService.linkToPost(mediaId, postId, userId);
        return ResponseEntity.ok(
                Map.of("message", "Media linked to post"));
    }

    // UNLINK FROM POST
    @DeleteMapping("/{mediaId}/unlink")
    @Operation(summary = "Unlink media file from its post")
    public ResponseEntity<Map<String, String>> unlinkFromPost(
            @PathVariable Integer mediaId,
            @RequestHeader("X-User-Id") Integer userId) {

        mediaService.unlinkFromPost(mediaId, userId);
        return ResponseEntity.ok(
                Map.of("message", "Media unlinked from post"));
    }

    // DELETE MEDIA
    @DeleteMapping("/{mediaId}")
    @Operation(summary = "Delete a media file")
    public ResponseEntity<Map<String, String>> deleteMedia(
            @PathVariable Integer mediaId,
            @RequestHeader("X-User-Id")   Integer userId,
            @RequestHeader("X-User-Role") String  userRole) {

        mediaService.deleteMedia(mediaId, userId, userRole);
        return ResponseEntity.ok(
                Map.of("message", "Media deleted successfully"));
    }

    // GET ALL (Admin)
    @GetMapping("/admin/all")
    @Operation(summary = "Get all media files - Admin only")
    public ResponseEntity<Page<MediaResponse>> getAllMedia(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader("X-User-Role") String userRole) {

        if (!"ADMIN".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.ok(
                mediaService.getAllMedia(page, size));
    }

    // CLEANUP (Admin)
    @DeleteMapping("/admin/cleanup")
    @Operation(summary = "Cleanup soft-deleted media records - Admin")
    public ResponseEntity<Map<String, String>> cleanup(
            @RequestHeader("X-User-Role") String userRole) {

        if (!"ADMIN".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        mediaService.cleanupDeletedMedia();
        return ResponseEntity.ok(
                Map.of("message", "Cleanup completed"));
    }

    // STORAGE USAGE
    @GetMapping("/storage/{userId}")
    @Operation(summary = "Get total storage usage for a user")
    public ResponseEntity<Map<String, Object>> getStorageUsage(
            @PathVariable Integer userId,
            @RequestHeader("X-User-Id")   Integer requestingUserId,
            @RequestHeader("X-User-Role") String  userRole) {

        // Users can check own storage; admins check anyone
        if (!userId.equals(requestingUserId)
                && !"ADMIN".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Long totalKb =
                mediaService.getTotalStorageKbByUploader(userId);
        double totalMb = totalKb / 1024.0;

        return ResponseEntity.ok(Map.of(
                "userId",    userId,
                "totalKb",   totalKb,
                "totalMb",   String.format("%.2f", totalMb),
                "formatted", String.format("%.2f MB", totalMb)
        ));
    }
}