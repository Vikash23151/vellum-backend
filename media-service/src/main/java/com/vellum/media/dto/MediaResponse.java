package com.vellum.media.dto;

import com.vellum.media.entity.Media;
import lombok.*;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaResponse {

    private Integer mediaId;
    private Integer uploaderId;
    private String  filename;
    private String  originalName;
    private String  url;
    private String  mimeType;
    private Long    sizeKb;
    private String  sizeFormatted;   // "1.5 MB" (easily readable)
    private String  altText;
    private Integer linkedPostId;
    private String  mediaType;       // "IMAGE" or "DOCUMENT"
    private LocalDateTime uploadedAt;

    public static MediaResponse fromEntity(Media media) {
        return MediaResponse.builder()
                .mediaId(media.getMediaId())
                .uploaderId(media.getUploaderId())
                .filename(media.getFilename())
                .originalName(media.getOriginalName())
                .url(media.getUrl())
                .mimeType(media.getMimeType())
                .sizeKb(media.getSizeKb())
                .sizeFormatted(formatSize(media.getSizeKb()))
                .altText(media.getAltText())
                .linkedPostId(media.getLinkedPostId())
                .mediaType(media.getMimeType()
                        .startsWith("image/") ? "IMAGE" : "DOCUMENT")
                .uploadedAt(media.getUploadedAt())
                .build();
    }

    private static String formatSize(Long sizeKb) {
        if (sizeKb == null) return "0 KB";
        if (sizeKb < 1024) return sizeKb + " KB";
        double mb = sizeKb / 1024.0;
        return String.format("%.1f MB", mb);
    }
}