package com.vellum.post.resource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vellum.post.dto.CreatePostRequest;
import com.vellum.post.dto.PostResponse;
import com.vellum.post.exception.CustomException;
import com.vellum.post.exception.GlobalExceptionHandler;
import com.vellum.post.service.PostService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PostResource Controller Tests")
class PostResourceTest {

    private MockMvc mockMvc;

    @Mock
    private PostService postService;

    @InjectMocks
    private PostResource postResource;

    private ObjectMapper objectMapper;
    private PostResponse samplePostResponse;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        mockMvc = MockMvcBuilders
                .standaloneSetup(postResource)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();

        samplePostResponse = PostResponse.builder()
                .postId(1)
                .authorId(42)
                .title("Test Post")
                .slug("test-post")
                .excerpt("Test excerpt")
                .status("PUBLISHED")
                .readTimeMin(2)
                .readTimeFormatted("2 min read")
                .viewCount(100)
                .likesCount(10)
                .build();
    }

    // GET /api/posts tests

    @Test
    @DisplayName("GET /api/posts: 200 OK returns paginated feed")
    void getPublishedPosts_returns200WithPage() throws Exception {
        var page = new PageImpl<>(
                List.of(samplePostResponse),
                PageRequest.of(0, 20),
                1
        );

        when(postService.getPublishedPosts(0, 20)).thenReturn(page);

        mockMvc.perform(get("/api/posts")
                        .param("page", "0")
                        .param("size", "20"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].title").value("Test Post"))
                .andExpect(jsonPath("$.content[0].slug").value("test-post"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    // POST /api/posts tests

    @Test
    @DisplayName("POST /api/posts: 201 Created on valid request")
    void createPost_validRequest_returns201() throws Exception {
        CreatePostRequest request = new CreatePostRequest();
        request.setTitle("New Test Post");
        request.setContent("<p>Content here</p>");

        when(postService.createPost(any(CreatePostRequest.class), eq(42)))
                .thenReturn(samplePostResponse);

        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", "42")
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Test Post"));
    }

    @Test
    @DisplayName("POST /api/posts: 400 Bad Request when title is blank")
    void createPost_blankTitle_returns400() throws Exception {
        CreatePostRequest request = new CreatePostRequest();
        request.setTitle("");  // blank
        request.setContent("<p>Content</p>");

        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", "42")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").exists());
    }

    // GET /api/posts/slug/{slug} tests

    @Test
    @DisplayName("GET /api/posts/slug/{slug}: 200 OK returns post")
    void getBySlug_exists_returns200() throws Exception {
        when(postService.getPostBySlug(eq("test-post"), any(), any()))
                .thenReturn(samplePostResponse);

        mockMvc.perform(get("/api/posts/slug/test-post"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("test-post"))
                .andExpect(jsonPath("$.title").value("Test Post"));
    }

    @Test
    @DisplayName("GET /api/posts/slug/{slug}: 404 Not Found for unknown slug")
    void getBySlug_notFound_returns404() throws Exception {
        when(postService.getPostBySlug(eq("no-post"), any(), any()))
                .thenThrow(new CustomException("Post not found", HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/api/posts/slug/no-post"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Post not found"));
    }

    // PUT /api/posts/{id}/publish tests

    @Test
    @DisplayName("PUT /api/posts/1/publish: 200 OK on success")
    void publishPost_success_returns200() throws Exception {
        PostResponse publishedResponse = PostResponse.builder()
                .postId(1)
                .status("PUBLISHED")
                .title("Test Post")
                .slug("test-post")
                .readTimeMin(2)
                .readTimeFormatted("2 min read")
                .build();

        when(postService.publishPost(1, 42)).thenReturn(publishedResponse);

        mockMvc.perform(put("/api/posts/1/publish")
                        .header("X-User-Id", "42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    @DisplayName("PUT /api/posts/1/publish: 403 Forbidden for non-owner")
    void publishPost_nonOwner_returns403() throws Exception {
        when(postService.publishPost(1, 99))
                .thenThrow(new CustomException(
                        "You don't have permission",
                        HttpStatus.FORBIDDEN));

        mockMvc.perform(put("/api/posts/1/publish")
                        .header("X-User-Id", "99"))
                .andExpect(status().isForbidden());
    }

    // POST /api/posts/{id}/like tests

    @Test
    @DisplayName("POST /api/posts/1/like: 200 OK on success")
    void likePost_success_returns200() throws Exception {
        doNothing().when(postService).likePost(1, 42);

        mockMvc.perform(post("/api/posts/1/like")
                        .header("X-User-Id", "42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Post liked"));
    }

    @Test
    @DisplayName("POST /api/posts/1/like: 409 Conflict when already liked")
    void likePost_alreadyLiked_returns409() throws Exception {
        doThrow(new CustomException("Already liked", HttpStatus.CONFLICT))
                .when(postService).likePost(1, 42);

        mockMvc.perform(post("/api/posts/1/like")
                        .header("X-User-Id", "42"))
                .andExpect(status().isConflict());
    }

    // DELETE /api/posts/{id} tests

    @Test
    @DisplayName("DELETE /api/posts/1: 200 OK on success")
    void deletePost_success_returns200() throws Exception {
        doNothing().when(postService).deletePost(1, 42);

        mockMvc.perform(delete("/api/posts/1")
                        .header("X-User-Id", "42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Post deleted successfully"));
    }

    // GET /api/posts/search tests

    @Test
    @DisplayName("GET /api/posts/search?q=test: 200 OK returns results")
    void searchPosts_returns200WithResults() throws Exception {
        var page = new PageImpl<>(
                List.of(samplePostResponse),
                PageRequest.of(0, 20),
                1
        );

        when(postService.searchPosts("test", 0, 20)).thenReturn(page);

        mockMvc.perform(get("/api/posts/search")
                        .param("q", "test")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Test Post"));
    }
}