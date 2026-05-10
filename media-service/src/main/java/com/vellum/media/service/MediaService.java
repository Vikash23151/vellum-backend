package com.vellum.media.service;

import com.vellum.media.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface MediaService {

    MediaResponse uploadMedia(MultipartFile file,
                              Integer uploaderId,
                              String altText) throws IOException;

    MediaResponse getMediaById(Integer mediaId);

    List<MediaResponse> getMediaByUploader(Integer uploaderId);

    List<MediaResponse> getMediaByPost(Integer postId);

    void deleteMedia(Integer mediaId,
                     Integer requestingUserId,
                     String requestingUserRole);

    MediaResponse updateAltText(Integer mediaId,
                                UpdateAltTextRequest request,
                                Integer requestingUserId);

    void linkToPost(Integer mediaId,
                    Integer postId,
                    Integer requestingUserId);

    void unlinkFromPost(Integer mediaId,
                        Integer requestingUserId);

    Page<MediaResponse> getAllMedia(int page, int size);

    void cleanupDeletedMedia();

    Long getTotalStorageKbByUploader(Integer uploaderId);
}