package com.vellum.media.repository;

import com.vellum.media.entity.Media;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("MediaRepository Tests")
class MediaRepositoryTest {

    @Autowired
    private MediaRepository mediaRepository;

    private Media activeMedia;
    private Media deletedMedia;
    private Media linkedMedia;

    @BeforeEach
    void setUp() {
        activeMedia = mediaRepository.save(Media.builder()
                .uploaderId(1)
                .filename("uuid-active.jpg")
                .originalName("active-photo.jpg")
                .url("https://s3.amazonaws.com/bucket/uploads/uuid-active.jpg")
                .mimeType("image/jpeg")
                .sizeKb(512L)
                .altText("Active photo")
                .isDeleted(false)
                .build());

        deletedMedia = mediaRepository.save(Media.builder()
                .uploaderId(1)
                .filename("uuid-deleted.jpg")
                .originalName("deleted-photo.jpg")
                .url("https://s3.amazonaws.com/bucket/uploads/uuid-deleted.jpg")
                .mimeType("image/jpeg")
                .sizeKb(256L)
                .altText("Deleted photo")
                .isDeleted(true)
                .build());

        linkedMedia = mediaRepository.save(Media.builder()
                .uploaderId(2)
                .filename("uuid-linked.png")
                .originalName("linked-image.png")
                .url("https://s3.amazonaws.com/bucket/uploads/uuid-linked.png")
                .mimeType("image/png")
                .sizeKb(1024L)
                .altText("Post featured image")
                .linkedPostId(100)
                .isDeleted(false)
                .build());
    }

    @Test
    @DisplayName("findByUploaderIdAndIsDeletedFalse: returns only active files")
    void findByUploaderId_returnsOnlyActiveFiles() {
        List<Media> result =
                mediaRepository
                        .findByUploaderIdAndIsDeletedFalseOrderByUploadedAtDesc(1);

        // deletedMedia has uploaderId=1 but isDeleted=true → excluded
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOriginalName())
                .isEqualTo("active-photo.jpg");
    }

    @Test
    @DisplayName("findByLinkedPostIdAndIsDeletedFalse: returns linked files")
    void findByLinkedPostId_returnsLinkedFiles() {
        List<Media> result =
                mediaRepository.findByLinkedPostIdAndIsDeletedFalse(100);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFilename())
                .isEqualTo("uuid-linked.png");
    }

    @Test
    @DisplayName("findByMediaIdAndIsDeletedFalse: returns active media")
    void findByMediaIdAndIsDeletedFalse_activeMedia() {
        Optional<Media> result =
                mediaRepository.findByMediaIdAndIsDeletedFalse(
                        activeMedia.getMediaId());

        assertThat(result).isPresent();
    }

    @Test
    @DisplayName("findByMediaIdAndIsDeletedFalse: returns empty for deleted")
    void findByMediaIdAndIsDeletedFalse_deletedMedia() {
        Optional<Media> result =
                mediaRepository.findByMediaIdAndIsDeletedFalse(
                        deletedMedia.getMediaId());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("softDelete: sets isDeleted to true")
    void softDelete_setsDeletedFlag() {
        mediaRepository.softDelete(activeMedia.getMediaId());

        Media updated = mediaRepository
                .findById(activeMedia.getMediaId()).get();
        assertThat(updated.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("updateAltText: updates only alt text")
    void updateAltText_updatesCorrectly() {
        mediaRepository.updateAltText(
                activeMedia.getMediaId(), "New alt text");

        Media updated = mediaRepository
                .findById(activeMedia.getMediaId()).get();
        assertThat(updated.getAltText()).isEqualTo("New alt text");
    }

    @Test
    @DisplayName("linkToPost: sets linkedPostId")
    void linkToPost_setsPostId() {
        mediaRepository.linkToPost(activeMedia.getMediaId(), 200);

        Media updated = mediaRepository
                .findById(activeMedia.getMediaId()).get();
        assertThat(updated.getLinkedPostId()).isEqualTo(200);
    }

    @Test
    @DisplayName("unlinkFromPost: clears linkedPostId")
    void unlinkFromPost_clearsPostId() {
        mediaRepository.unlinkFromPost(linkedMedia.getMediaId());

        Media updated = mediaRepository
                .findById(linkedMedia.getMediaId()).get();
        assertThat(updated.getLinkedPostId()).isNull();
    }

    @Test
    @DisplayName("getTotalSizeKbByUploader: returns sum of active files")
    void getTotalSizeKbByUploader_returnsTotalSize() {
        // uploaderId=1: activeMedia(512KB) only (deletedMedia excluded)
        Long total =
                mediaRepository.getTotalSizeKbByUploader(1);
        assertThat(total).isEqualTo(512L);
    }

    @Test
    @DisplayName("findByIsDeletedTrue: returns only soft-deleted records")
    void findByIsDeletedTrue_returnsDeletedRecords() {
        List<Media> deleted = mediaRepository.findByIsDeletedTrue();

        assertThat(deleted).hasSize(1);
        assertThat(deleted.get(0).getOriginalName())
                .isEqualTo("deleted-photo.jpg");
    }

    @Test
    @DisplayName("findByIsDeletedFalseOrderByUploadedAtDesc: paginated")
    void findAllActive_paginated() {
        Page<Media> page = mediaRepository
                .findByIsDeletedFalseOrderByUploadedAtDesc(
                        PageRequest.of(0, 10));

        // activeMedia + linkedMedia = 2 active records
        assertThat(page.getContent()).hasSize(2);
    }
}