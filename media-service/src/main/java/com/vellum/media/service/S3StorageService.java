package com.vellum.media.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3StorageService {

    private final S3Client s3Client;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${aws.s3.region}")
    private String region;

    @Value("${aws.s3.cdn-url:}")
    private String cdnUrl;

    @Value("${app.media.s3-folder:uploads/}")
    private String s3Folder;

    public String uploadFile(MultipartFile file,
                             String mimeType) throws IOException {
        // Generate unique filename to avoid S3 key collisions
        String extension = getExtension(
                file.getOriginalFilename());
        String uniqueFilename = UUID.randomUUID() + extension;
        String s3Key = s3Folder + uniqueFilename;

        log.info("Uploading to S3: key={} size={}KB",
                s3Key, file.getSize() / 1024);

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .contentType(mimeType)
                .contentLength(file.getSize())
                .acl(ObjectCannedACL.PUBLIC_READ)
                .build();

        s3Client.putObject(
                putRequest,
                RequestBody.fromInputStream(
                        file.getInputStream(), file.getSize()));

        String url = buildPublicUrl(s3Key);
        log.info("Uploaded to S3: {}", url);
        return url;
    }

    public void deleteFile(String fileUrl) {
        try {
            String s3Key = extractS3Key(fileUrl);

            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();

            s3Client.deleteObject(deleteRequest);
            log.info("Deleted from S3: key={}", s3Key);

        } catch (Exception e) {
            // Log but don't fail — file might already be gone
            log.error("Failed to delete from S3: {} | {}",
                    fileUrl, e.getMessage());
        }
    }

    public boolean fileExists(String s3Key) {
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    // Private Helpers

    /*
     * Build public URL from S3 key.
     * If CDN URL configured: use CloudFront URL (faster).
     * Otherwise: use direct S3 URL.
     *
     * S3 URL format:
     * https://{bucket}.s3.{region}.amazonaws.com/{key}
     *
     * CloudFront URL format:
     * https://{distribution-id}.cloudfront.net/{key}
     */
    private String buildPublicUrl(String s3Key) {
        if (cdnUrl != null && !cdnUrl.isBlank()) {
            return cdnUrl.stripTrailing() + "/" + s3Key;
        }
        return String.format(
                "https://%s.s3.%s.amazonaws.com/%s",
                bucketName, region, s3Key);
    }

    /*
     * Extract S3 key from full URL.
     * URL: "https://bucket.s3.region.amazonaws.com/uploads/uuid.jpg"
     * Key: "uploads/uuid.jpg"
     *
     * Finds the folder prefix in URL and takes everything after.
     */
    private String extractS3Key(String url) {
        int folderIndex = url.indexOf(s3Folder);
        if (folderIndex >= 0) {
            return url.substring(folderIndex);
        }
        // Fallback: take path after domain
        return url.substring(url.indexOf('/', 8) + 1);
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf("."))
                .toLowerCase();
    }
}