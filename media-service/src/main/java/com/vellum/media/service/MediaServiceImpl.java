package com.vellum.media.service;

import com.vellum.media.dto.*;
import com.vellum.media.entity.Media;
import com.vellum.media.exception.CustomException;
import com.vellum.media.repository.MediaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MediaServiceImpl implements MediaService {

    private final MediaRepository mediaRepository;
    private final S3StorageService s3StorageService;
    private final TransactionTemplate transactionTemplate;

    private final Tika tika = new Tika();

    @Value("${app.media.max-size-bytes:10485760}")
    private long maxSizeBytes;

    @Value("${app.media.allowed-types}")
    private List<String> allowedTypes;

    private static final int MAX_PAGE_SIZE = 100;
    private static final String ADMIN_ROLE = "ADMIN";

    @Override
    public MediaResponse uploadMedia(MultipartFile file, Integer uploaderId, String altText) throws IOException {
        log.info("Upload request: file={} uploader={}", file.getOriginalFilename(), uploaderId);

        // --- VALIDATIONS ---

        // 1. Empty check (Improved message)
        if (file.isEmpty()) {
            throw new CustomException("Cannot upload an empty file", HttpStatus.BAD_REQUEST);
        }

        // 2. Restored robust size validation
        if (file.getSize() > maxSizeBytes) {
            long maxMb = maxSizeBytes / 1024 / 1024;
            throw new CustomException(
                    String.format("File size exceeds %dMB limit. Uploaded: %.1f MB",
                            maxMb, file.getSize() / 1024.0 / 1024.0),
                    HttpStatus.BAD_REQUEST);
        }

        // 3. Memory-efficient MIME detection
        String detectedMimeType;
        try (InputStream is = file.getInputStream()) {
            Metadata metadata = new Metadata();
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, file.getOriginalFilename());
            detectedMimeType = tika.detect(is, metadata);
        }

        if (!isAllowedType(detectedMimeType)) {
            throw new CustomException(
                    "File type '" + detectedMimeType + "' is not allowed. Allowed: JPEG, PNG, GIF, WebP, PDF",
                    HttpStatus.BAD_REQUEST);
        }

        // --- UPLOAD FLOW ---

        // S3 Upload (Outside DB transaction)
        String publicUrl = s3StorageService.uploadFile(file, detectedMimeType);

        try {
            // DB Save (Inside DB transaction via Template)
            MediaResponse response = transactionTemplate.execute(status -> {
                Media media = Media.builder()
                        .uploaderId(uploaderId)
                        .filename(extractFilenameFromUrl(publicUrl))
                        .originalName(sanitizeFilename(file.getOriginalFilename()))
                        .url(publicUrl)
                        .mimeType(detectedMimeType)
                        .sizeKb(Math.max(1, file.getSize() / 1024))
                        .altText(altText != null ? altText : "")
                        .isDeleted(false)
                        .build();

                return MediaResponse.fromEntity(mediaRepository.save(media));
            });

            // Replaced Objects.requireNonNull with cleaner API Exception
            if (response == null) {
                throw new CustomException("Failed to save media metadata to database", HttpStatus.INTERNAL_SERVER_ERROR);
            }

            return response;

        } catch (Exception e) {
            log.error("DB Save failed, cleaning orphaned S3 file: {}", publicUrl);
            s3StorageService.deleteFile(publicUrl);
            throw e;
        }
    }

    @Override
    @Transactional
    public void deleteMedia(Integer mediaId, Integer requestingUserId, String requestingUserRole) {
        Media media = findActiveMediaOrThrow(mediaId);

        if (!ADMIN_ROLE.equals(requestingUserRole) && !media.getUploaderId().equals(requestingUserId)) {
            throw new CustomException("You don't have permission to delete this file", HttpStatus.FORBIDDEN);
        }

        // Reverted to S3 delete FIRST.
        // If S3 fails, DB transaction is rolled back, and the file remains actively linked.
        s3StorageService.deleteFile(media.getUrl());
        mediaRepository.softDelete(mediaId);

        log.info("Media soft-deleted: id={} by userId={}", mediaId, requestingUserId);
    }

    // --- READ METHODS ---

    @Override
    public MediaResponse getMediaById(Integer mediaId) {
        return MediaResponse.fromEntity(findActiveMediaOrThrow(mediaId));
    }

    @Override
    public List<MediaResponse> getMediaByUploader(Integer uploaderId) {
        return mediaRepository.findByUploaderIdAndIsDeletedFalseOrderByUploadedAtDesc(uploaderId)
                .stream().map(MediaResponse::fromEntity).collect(Collectors.toList());
    }

    @Override
    public List<MediaResponse> getMediaByPost(Integer postId) {
        return mediaRepository.findByLinkedPostIdAndIsDeletedFalse(postId)
                .stream().map(MediaResponse::fromEntity).collect(Collectors.toList());
    }

    @Override
    public Page<MediaResponse> getAllMedia(int page, int size) {
        int validatedSize = Math.min(size, MAX_PAGE_SIZE);
        return mediaRepository.findByIsDeletedFalseOrderByUploadedAtDesc(PageRequest.of(page, validatedSize))
                .map(MediaResponse::fromEntity);
    }

    // --- WRITE METHODS ---

    @Override
    @Transactional
    public MediaResponse updateAltText(Integer mediaId, UpdateAltTextRequest request, Integer requestingUserId) {
        Media media = findActiveMediaOrThrow(mediaId);
        if (!media.getUploaderId().equals(requestingUserId)) {
            throw new CustomException("You can only update alt text for your own files", HttpStatus.FORBIDDEN);
        }
        mediaRepository.updateAltText(mediaId, request.getAltText() != null ? request.getAltText() : "");
        return MediaResponse.fromEntity(findActiveMediaOrThrow(mediaId));
    }

    @Override
    @Transactional
    public void linkToPost(Integer mediaId, Integer postId, Integer requestingUserId) {
        Media media = findActiveMediaOrThrow(mediaId);
        if (!media.getUploaderId().equals(requestingUserId)) {
            throw new CustomException("You can only link your own files to posts", HttpStatus.FORBIDDEN);
        }
        mediaRepository.linkToPost(mediaId, postId);
    }

    @Override
    @Transactional
    public void unlinkFromPost(Integer mediaId, Integer requestingUserId) {
        Media media = findActiveMediaOrThrow(mediaId);
        if (!media.getUploaderId().equals(requestingUserId)) {
            throw new CustomException("You can only unlink your own files", HttpStatus.FORBIDDEN);
        }
        mediaRepository.unlinkFromPost(mediaId);
    }

    @Override
    @Transactional
    public void cleanupDeletedMedia() {
        List<Media> deletedMedia = mediaRepository.findByIsDeletedTrue();
        mediaRepository.deleteAll(deletedMedia);
    }

    @Override
    public Long getTotalStorageKbByUploader(Integer uploaderId) {
        return mediaRepository.getTotalSizeKbByUploader(uploaderId);
    }

    // --- PRIVATE HELPERS ---

    private Media findActiveMediaOrThrow(Integer mediaId) {
        return mediaRepository.findByMediaIdAndIsDeletedFalse(mediaId)
                .orElseThrow(() -> new CustomException("Media not found: " + mediaId, HttpStatus.NOT_FOUND));
    }

    private boolean isAllowedType(String mimeType) {
        String baseType = mimeType.split(";")[0].trim().toLowerCase();
        return allowedTypes.stream().anyMatch(allowed -> allowed.equalsIgnoreCase(baseType));
    }

    private String extractFilenameFromUrl(String url) {
        return url.substring(url.lastIndexOf('/') + 1);
    }

    private String sanitizeFilename(String originalFilename) {
        if (originalFilename == null) return "unnamed";
        String sanitized = originalFilename.replaceAll(".*[/\\\\]", "").trim();
        return sanitized.isEmpty() ? "unnamed" : sanitized;
    }
}