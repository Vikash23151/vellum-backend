package com.vellum.media.service;

import com.vellum.media.dto.*;
import com.vellum.media.entity.Media;
import com.vellum.media.exception.CustomException;
import com.vellum.media.repository.MediaRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MediaServiceImpl Unit Tests")
class MediaServiceImplTest {

    @Mock private MediaRepository  mediaRepository;
    @Mock private S3StorageService s3StorageService;
    @Mock private TransactionTemplate transactionTemplate;

    @InjectMocks
    private MediaServiceImpl mediaService;

    private Media testMedia;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(
                mediaService, "maxSizeBytes", 10485760L);
        ReflectionTestUtils.setField(
                mediaService, "allowedTypes",
                List.of("image/jpeg", "image/png",
                        "image/gif", "image/webp",
                        "application/pdf"));

        testMedia = Media.builder()
                .mediaId(1)
                .uploaderId(42)
                .filename("uuid-test.jpg")
                .originalName("test-photo.jpg")
                .url("https://bucket.s3.amazonaws.com/uploads/uuid-test.jpg")
                .mimeType("image/jpeg")
                .sizeKb(512L)
                .altText("Test photo")
                .isDeleted(false)
                .build();

    }

    // uploadMedia() tests

    @Test
    @DisplayName("uploadMedia: success with valid JPEG")
    void uploadMedia_validJpeg_success() throws IOException {
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null); // This actually runs the DB save logic in the test
        });
        /*
         * MockMultipartFile: Spring's fake MultipartFile for tests.
         * Avoids needing a real file on disk.
         *
         * Parameters:
         * - field name: "file"
         * - original filename: "photo.jpg"
         * - content type: "image/jpeg" (browser-declared, not trusted)
         * - content: fake JPEG bytes (starts with FF D8 FF = JPEG magic)
         */
        byte[] jpegBytes = new byte[]{
                (byte)0xFF, (byte)0xD8, (byte)0xFF, // JPEG magic bytes
                0x00, 0x01, 0x02, 0x03               // dummy data
        };

        MockMultipartFile mockFile = new MockMultipartFile(
                "file",
                "photo.jpg",
                "image/jpeg",
                jpegBytes);

        when(s3StorageService.uploadFile(any(), anyString()))
                .thenReturn("https://bucket.s3.amazonaws.com/uploads/uuid.jpg");
        when(mediaRepository.save(any(Media.class)))
                .thenReturn(testMedia);

        MediaResponse result = mediaService.uploadMedia(
                mockFile, 42, "A test photo");

        assertThat(result).isNotNull();
        assertThat(result.getMimeType()).isEqualTo("image/jpeg");
        verify(s3StorageService, times(1))
                .uploadFile(any(), anyString());
        verify(mediaRepository, times(1)).save(any(Media.class));
    }

    @Test
    @DisplayName("uploadMedia: throws 400 when file is empty")
    void uploadMedia_emptyFile_throwsBadRequest() throws IOException {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.jpg",
                "image/jpeg", new byte[0]);

        assertThatThrownBy(() ->
                mediaService.uploadMedia(emptyFile, 42, null))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(
                        ((CustomException) ex).getStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        verify(s3StorageService, never()).uploadFile(any(), any());
    }

    @Test
    @DisplayName("uploadMedia: throws 400 when file exceeds size limit")
    void uploadMedia_tooLarge_throwsBadRequest() {
        // Create a file larger than 10MB limit
        byte[] largeContent = new byte[11 * 1024 * 1024]; // 11MB

        MockMultipartFile largeFile = new MockMultipartFile(
                "file", "large.jpg",
                "image/jpeg", largeContent);

        assertThatThrownBy(() ->
                mediaService.uploadMedia(largeFile, 42, null))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("exceeds")
                .satisfies(ex -> assertThat(
                        ((CustomException) ex).getStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // getMediaById() tests

    @Test
    @DisplayName("getMediaById: returns media when found and active")
    void getMediaById_active_returnsMedia() {
        when(mediaRepository.findByMediaIdAndIsDeletedFalse(1))
                .thenReturn(Optional.of(testMedia));

        MediaResponse result = mediaService.getMediaById(1);

        assertThat(result).isNotNull();
        assertThat(result.getMediaId()).isEqualTo(1);
        assertThat(result.getOriginalName()).isEqualTo("test-photo.jpg");
    }

    @Test
    @DisplayName("getMediaById: throws 404 for deleted media")
    void getMediaById_deleted_throws404() {
        when(mediaRepository.findByMediaIdAndIsDeletedFalse(1))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> mediaService.getMediaById(1))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(
                        ((CustomException) ex).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    // deleteMedia() tests

    @Test
    @DisplayName("deleteMedia: owner can delete own file")
    void deleteMedia_owner_success() {
        when(mediaRepository.findByMediaIdAndIsDeletedFalse(1))
                .thenReturn(Optional.of(testMedia));

        // Owner (uploaderId=42) deletes
        mediaService.deleteMedia(1, 42, "AUTHOR");

        verify(s3StorageService, times(1))
                .deleteFile(testMedia.getUrl());
        verify(mediaRepository, times(1)).softDelete(1);
    }

    @Test
    @DisplayName("deleteMedia: admin can delete any file")
    void deleteMedia_admin_success() {
        when(mediaRepository.findByMediaIdAndIsDeletedFalse(1))
                .thenReturn(Optional.of(testMedia));

        // Admin (userId=99, not owner) deletes
        mediaService.deleteMedia(1, 99, "ADMIN");

        verify(s3StorageService, times(1))
                .deleteFile(testMedia.getUrl());
        verify(mediaRepository, times(1)).softDelete(1);
    }

    @Test
    @DisplayName("deleteMedia: throws 403 when non-owner non-admin")
    void deleteMedia_nonOwnerNonAdmin_throwsForbidden() {
        when(mediaRepository.findByMediaIdAndIsDeletedFalse(1))
                .thenReturn(Optional.of(testMedia));

        assertThatThrownBy(() ->
                mediaService.deleteMedia(1, 99, "READER"))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(
                        ((CustomException) ex).getStatus())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        verify(s3StorageService, never()).deleteFile(any());
        verify(mediaRepository, never()).softDelete(any());
    }

    // updateAltText() tests

    @Test
    @DisplayName("updateAltText: owner can update alt text")
    void updateAltText_owner_success() {
        UpdateAltTextRequest request = new UpdateAltTextRequest();
        request.setAltText("Updated alt text description");

        when(mediaRepository.findByMediaIdAndIsDeletedFalse(1))
                .thenReturn(Optional.of(testMedia));

        mediaService.updateAltText(1, request, 42);

        verify(mediaRepository, times(1))
                .updateAltText(1, "Updated alt text description");
    }

    @Test
    @DisplayName("updateAltText: throws 403 for non-owner")
    void updateAltText_nonOwner_throwsForbidden() {
        UpdateAltTextRequest request = new UpdateAltTextRequest();
        request.setAltText("Trying to update someone else's file");

        when(mediaRepository.findByMediaIdAndIsDeletedFalse(1))
                .thenReturn(Optional.of(testMedia));

        assertThatThrownBy(() ->
                mediaService.updateAltText(1, request, 99))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(
                        ((CustomException) ex).getStatus())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

   // linkToPost() tests

    @Test
    @DisplayName("linkToPost: owner can link file to post")
    void linkToPost_owner_success() {
        when(mediaRepository.findByMediaIdAndIsDeletedFalse(1))
                .thenReturn(Optional.of(testMedia));

        mediaService.linkToPost(1, 100, 42);

        verify(mediaRepository, times(1)).linkToPost(1, 100);
    }

    // getMediaByUploader() tests

    @Test
    @DisplayName("getMediaByUploader: returns uploader's files")
    void getMediaByUploader_returnsFiles() {
        when(mediaRepository
                .findByUploaderIdAndIsDeletedFalseOrderByUploadedAtDesc(42))
                .thenReturn(List.of(testMedia));

        List<MediaResponse> results =
                mediaService.getMediaByUploader(42);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getOriginalName())
                .isEqualTo("test-photo.jpg");
    }
}