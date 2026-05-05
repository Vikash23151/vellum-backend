package com.vellum.comment.resource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vellum.comment.dto.AddCommentRequest;
import com.vellum.comment.dto.CommentResponse;
import com.vellum.comment.dto.UpdateCommentRequest;
import com.vellum.comment.exception.CustomException;
import com.vellum.comment.exception.GlobalExceptionHandler;
import com.vellum.comment.service.CommentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("CommentResource Controller Tests")
class CommentResourceTest {

    private MockMvc mockMvc;

    @Mock
    private CommentService commentService;

    @InjectMocks
    private CommentResource commentResource;

    private ObjectMapper objectMapper;
    private CommentResponse sampleResponse;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        mockMvc = MockMvcBuilders
                .standaloneSetup(commentResource)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        sampleResponse = CommentResponse.builder()
                .commentId(1)
                .postId(100)
                .authorId(42)
                .content("Test comment content")
                .status("APPROVED")
                .likesCount(0)
                .isEdited(false)
                .replies(List.of())
                .build();
    }

    @Test
    @DisplayName("POST /api/comments: 201 Created on valid request")
    void addComment_validRequest_returns201() throws Exception {
        AddCommentRequest request = new AddCommentRequest();
        request.setPostId(100);
        request.setContent("This is my comment");

        when(commentService.addComment(any(AddCommentRequest.class), eq(42))).thenReturn(sampleResponse);

        mockMvc.perform(post("/api/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", "42")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.commentId").value(1))
                .andExpect(jsonPath("$.content").value("Test comment content"))
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    @DisplayName("PUT /api/comments/1: 403 when non-owner tries to edit")
    void updateComment_nonOwner_returns403() throws Exception {
        UpdateCommentRequest request = new UpdateCommentRequest();
        request.setContent("Cannot edit");

        when(commentService.updateComment(eq(1), any(UpdateCommentRequest.class), eq(99)))
                .thenThrow(new CustomException("You can only edit your own comments", HttpStatus.FORBIDDEN));

        mockMvc.perform(put("/api/comments/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", "99")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /api/comments/1: 200 OK on success")
    void deleteComment_success_returns200() throws Exception {
        doNothing().when(commentService).deleteComment(1, 42, "READER");

        mockMvc.perform(delete("/api/comments/1")
                        .header("X-User-Id", "42")
                        .header("X-User-Role", "READER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Comment deleted successfully"));
    }

    @Test
    @DisplayName("POST /api/comments/1/like: 409 when already liked")
    void likeComment_alreadyLiked_returns409() throws Exception {
        doThrow(new CustomException("Already liked", HttpStatus.CONFLICT))
                .when(commentService).likeComment(1, 42);

        mockMvc.perform(post("/api/comments/1/like")
                        .header("X-User-Id", "42"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("GET /api/comments/post/100/count: returns count")
    void getCount_returns200WithCount() throws Exception {
        when(commentService.getCommentCount(100)).thenReturn(7L);

        mockMvc.perform(get("/api/comments/post/100/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(7));
    }
}
