package com.vellum.comment.service;

import com.vellum.comment.dto.AddCommentRequest;
import com.vellum.comment.dto.CommentResponse;
import com.vellum.comment.dto.UpdateCommentRequest;
import com.vellum.comment.entity.Comment;
import com.vellum.comment.exception.CustomException;
import com.vellum.comment.repository.CommentLikeRepository;
import com.vellum.comment.repository.CommentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CommentServiceImpl Unit Tests")
class CommentServiceImplTest {

    @Mock private CommentRepository commentRepository;
    @Mock private CommentLikeRepository commentLikeRepository;
    @Mock private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private CommentServiceImpl commentService;

    private Comment approvedComment;
    private Comment pendingComment;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(commentService, "editWindowMinutes", 15);
        ReflectionTestUtils.setField(commentService, "moderationMode", "AUTO_APPROVE");

        approvedComment = Comment.builder()
                .commentId(1)
                .postId(100)
                .authorId(42)
                .parentCommentId(null)
                .content("This is a test comment")
                .status(Comment.CommentStatus.APPROVED)
                .likesCount(0)
                .createdAt(LocalDateTime.now())
                .build();

        pendingComment = Comment.builder()
                .commentId(2)
                .postId(100)
                .authorId(43)
                .parentCommentId(null)
                .content("Pending comment")
                .status(Comment.CommentStatus.PENDING)
                .likesCount(0)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("addComment: creates APPROVED comment in AUTO_APPROVE mode")
    void addComment_autoApprove_createsApproved() {
        AddCommentRequest request = new AddCommentRequest();
        request.setPostId(100);
        request.setContent("Hello world comment");

        when(commentRepository.save(any(Comment.class))).thenReturn(approvedComment);

        CommentResponse result = commentService.addComment(request, 42);

        assertThat(result).isNotNull();
        verify(commentRepository, times(1)).save(any(Comment.class));
        verify(rabbitTemplate, times(1)).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    @DisplayName("updateComment: throws 403 when non-owner tries to edit")
    void updateComment_nonOwner_throwsForbidden() {
        UpdateCommentRequest request = new UpdateCommentRequest();
        request.setContent("Hacking your comment");

        when(commentRepository.findById(1)).thenReturn(Optional.of(approvedComment));

        assertThatThrownBy(() -> commentService.updateComment(1, request, 99))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("deleteComment: non-owner non-admin throws 403")
    void deleteComment_nonOwnerNonAdmin_throwsForbidden() {
        when(commentRepository.findById(1)).thenReturn(Optional.of(approvedComment));

        assertThatThrownBy(() -> commentService.deleteComment(1, 99, "READER"))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));

        verify(commentRepository, never()).softDeleteComment(anyInt());
    }

    @Test
    @DisplayName("approveComment: ADMIN can approve PENDING comment")
    void approveComment_admin_approvesPending() {
        when(commentRepository.findById(2)).thenReturn(Optional.of(pendingComment));
        when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> inv.getArgument(0));

        CommentResponse result = commentService.approveComment(2, 99, "ADMIN");

        assertThat(result.getStatus()).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("likeComment: throws 409 when already liked")
    void likeComment_alreadyLiked_throwsConflict() {
        when(commentRepository.findById(1)).thenReturn(Optional.of(approvedComment));
        when(commentLikeRepository.existsByCommentIdAndUserId(1, 50)).thenReturn(true);

        assertThatThrownBy(() -> commentService.likeComment(1, 50))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    @DisplayName("getCommentCount: returns count from repository")
    void getCommentCount_returnsCorrectCount() {
        when(commentRepository.countApprovedByPostId(100)).thenReturn(5L);

        long count = commentService.getCommentCount(100);

        assertThat(count).isEqualTo(5L);
    }
}
