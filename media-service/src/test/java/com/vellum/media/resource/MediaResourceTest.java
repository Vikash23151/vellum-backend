package com.vellum.media.resource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vellum.media.dto.*;
import com.vellum.media.exception.CustomException;
import com.vellum.media.exception.GlobalExceptionHandler;
import com.vellum.media.service.MediaService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MediaResource Controller Tests")
class MediaResourceTest {

    private MockMvc mockMvc;

    @Mock
    private MediaService mediaService;

    @InjectMocks
    private MediaResource mediaResource;

    private final ObjectMapper objectMapper =
            new ObjectMapper().registerModule(new JavaTimeModule());

    private MediaResponse sampleResponse;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(mediaResource)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        sampleResponse = MediaResponse.builder()
                .mediaId(1)
                .uploaderId(42)
                .filename("uuid-test.jpg")
                .originalName("test-photo.jpg")
                .url("https://bucket.s3.amazonaws.com/uuid-test.jpg")
                .mimeType("image/jpeg")
                .sizeKb(512L)
                .sizeFormatted("512 KB")
                .altText("Test photo")
                .mediaType("IMAGE")
                .uploadedAt(LocalDateTime.now())
                .build();
    }

   // POST /api/media/upload tests

    @Test
    @DisplayName("POST /api/media/upload: 201 Created on success")
    void uploadMedia_success_returns201() throws Exception {
        /*
         * MockMultipartFile for controller test.
         * MockMvc.multipart() sends it as multipart/form-data.
         */
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "photo.jpg",
                "image/jpeg",
                "fake-jpeg-content".getBytes());

        when(mediaService.uploadMedia(any(), eq(42), any()))
                .thenReturn(sampleResponse);

        mockMvc.perform(multipart("/api/media/upload")
                        .file(file)
                        .param("altText", "A beautiful photo")
                        .header("X-User-Id", "42"))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mediaId").value(1))
                .andExpect(jsonPath("$.originalName")
                        .value("test-photo.jpg"))
                .andExpect(jsonPath("$.mimeType")
                        .value("image/jpeg"))
                .andExpect(jsonPath("$.mediaType")
                        .value("IMAGE"));
    }

    // GET /api/media/my tests

    @Test
    @DisplayName("GET /api/media/my: 200 OK returns user's files")
    void getMyMedia_returns200() throws Exception {
        when(mediaService.getMediaByUploader(42))
                .thenReturn(List.of(sampleResponse));

        mockMvc.perform(get("/api/media/my")
                        .header("X-User-Id", "42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].mediaId").value(1))
                .andExpect(jsonPath("$[0].sizeFormatted")
                        .value("512 KB"));
    }

    // GET /api/media/{id} tests

    @Test
    @DisplayName("GET /api/media/1: 200 OK returns media")
    void getById_returns200() throws Exception {
        when(mediaService.getMediaById(1)).thenReturn(sampleResponse);

        mockMvc.perform(get("/api/media/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filename")
                        .value("uuid-test.jpg"));
    }

    @Test
    @DisplayName("GET /api/media/999: 404 Not Found")
    void getById_notFound_returns404() throws Exception {
        when(mediaService.getMediaById(999))
                .thenThrow(new CustomException(
                        "Media not found", HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/api/media/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error")
                        .value("Media not found"));
    }

   // PUT /api/media/{id}/alt-text tests

    @Test
    @DisplayName("PUT /api/media/1/alt-text: 200 OK on success")
    void updateAltText_success_returns200() throws Exception {
        UpdateAltTextRequest request = new UpdateAltTextRequest();
        request.setAltText("Updated description");

        MediaResponse updated = MediaResponse.builder()
                .mediaId(1).altText("Updated description")
                .originalName("test-photo.jpg")
                .mimeType("image/jpeg").sizeKb(512L)
                .sizeFormatted("512 KB").mediaType("IMAGE")
                .build();

        when(mediaService.updateAltText(eq(1), any(), eq(42)))
                .thenReturn(updated);

        mockMvc.perform(put("/api/media/1/alt-text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", "42")
                        .content(objectMapper
                                .writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.altText")
                        .value("Updated description"));
    }

    // DELETE /api/media/{id} tests

    @Test
    @DisplayName("DELETE /api/media/1: 200 OK on success")
    void deleteMedia_success_returns200() throws Exception {
        doNothing().when(mediaService).deleteMedia(1, 42, "AUTHOR");

        mockMvc.perform(delete("/api/media/1")
                        .header("X-User-Id",   "42")
                        .header("X-User-Role", "AUTHOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("Media deleted successfully"));
    }

    @Test
    @DisplayName("DELETE /api/media/1: 403 for non-owner")
    void deleteMedia_nonOwner_returns403() throws Exception {
        doThrow(new CustomException(
                "Permission denied", HttpStatus.FORBIDDEN))
                .when(mediaService).deleteMedia(1, 99, "READER");

        mockMvc.perform(delete("/api/media/1")
                        .header("X-User-Id",   "99")
                        .header("X-User-Role", "READER"))
                .andExpect(status().isForbidden());
    }


    // POST /api/media/{id}/link/{postId} tests

    @Test
    @DisplayName("POST /api/media/1/link/100: 200 OK on success")
    void linkToPost_success_returns200() throws Exception {
        doNothing().when(mediaService).linkToPost(1, 100, 42);

        mockMvc.perform(post("/api/media/1/link/100")
                        .header("X-User-Id", "42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("Media linked to post"));
    }

    // GET /api/media/admin/all tests

    @Test
    @DisplayName("GET /api/media/admin/all: 403 for non-ADMIN")
    void getAllMedia_nonAdmin_returns403() throws Exception {
        mockMvc.perform(get("/api/media/admin/all")
                        .header("X-User-Role", "AUTHOR"))
                .andExpect(status().isForbidden());
    }
}