package com.vellum.comment.repository;

import com.vellum.comment.entity.Comment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("CommentRepository Tests")
class CommentRepositoryTest {

    @Autowired
    private CommentRepository commentRepository;

    private Comment topLevel1;
    private Comment topLevel2;
    private Comment reply1;

    @BeforeEach
    void setUp() {
        topLevel1 = commentRepository.save(Comment.builder()
                .postId(1).authorId(10)
                .parentCommentId(null)
                .content("Top level comment one")
                .status(Comment.CommentStatus.APPROVED)
                .build());

        topLevel2 = commentRepository.save(Comment.builder()
                .postId(1).authorId(11)
                .parentCommentId(null)
                .content("Top level comment two")
                .status(Comment.CommentStatus.APPROVED)
                .build());

        reply1 = commentRepository.save(Comment.builder()
                .postId(1).authorId(12)
                .parentCommentId(topLevel1.getCommentId())
                .content("Reply to first comment")
                .status(Comment.CommentStatus.APPROVED)
                .build());

        commentRepository.save(Comment.builder()
                .postId(1).authorId(13)
                .parentCommentId(null)
                .content("Pending comment")
                .status(Comment.CommentStatus.PENDING)
                .build());
    }

    @Test
    @DisplayName("findTopLevelByPostId: returns only approved top-level comments")
    void findTopLevelByPostId_returnsApprovedTopLevel() {
        List<Comment> result = commentRepository.findTopLevelByPostId(1);

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(c -> c.getParentCommentId() == null);
        assertThat(result).allMatch(c -> c.getStatus() == Comment.CommentStatus.APPROVED);
    }

    @Test
    @DisplayName("findRepliesByParentId: returns replies for a comment")
    void findRepliesByParentId_returnsReplies() {
        List<Comment> replies = commentRepository.findRepliesByParentId(topLevel1.getCommentId());

        assertThat(replies).hasSize(1);
        assertThat(replies.get(0).getContent()).isEqualTo("Reply to first comment");
    }

    @Test
    @DisplayName("findRepliesByParentId: returns empty when no replies")
    void findRepliesByParentId_noReplies_returnsEmpty() {
        List<Comment> replies = commentRepository.findRepliesByParentId(topLevel2.getCommentId());

        assertThat(replies).isEmpty();
    }

    @Test
    @DisplayName("countApprovedByPostId: counts only approved comments")
    void countApprovedByPostId_countsCorrectly() {
        long count = commentRepository.countApprovedByPostId(1);
        assertThat(count).isEqualTo(3);
    }

    @Test
    @DisplayName("softDeleteComment: sets status to DELETED and clears content")
    void softDeleteComment_setsDeletedStatus() {
        commentRepository.softDeleteComment(topLevel1.getCommentId());

        Comment deleted = commentRepository.findById(topLevel1.getCommentId()).orElseThrow();

        assertThat(deleted.getStatus()).isEqualTo(Comment.CommentStatus.DELETED);
        assertThat(deleted.getContent()).isEqualTo("This comment has been deleted");
    }

    @Test
    @DisplayName("softDeleteRepliesByParentId: soft deletes all child replies")
    void softDeleteRepliesByParentId_deletesAllReplies() {
        commentRepository.softDeleteRepliesByParentId(topLevel1.getCommentId());

        Comment deletedReply = commentRepository.findById(reply1.getCommentId()).orElseThrow();

        assertThat(deletedReply.getStatus()).isEqualTo(Comment.CommentStatus.DELETED);
    }

    @Test
    @DisplayName("deleteAllByPostId: hard deletes all comments for a post")
    void deleteAllByPostId_deletesAll() {
        commentRepository.deleteAllByPostId(1);

        List<Comment> remaining = commentRepository.findAll();
        assertThat(remaining).isEmpty();
    }

    @Test
    @DisplayName("incrementLikesCount: atomically increments")
    void incrementLikesCount_increments() {
        commentRepository.incrementLikesCount(topLevel1.getCommentId());

        Comment updated = commentRepository.findById(topLevel1.getCommentId()).orElseThrow();
        assertThat(updated.getLikesCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("findAllByPostIdForModeration: includes PENDING comments")
    void findAllByPostIdForModeration_includesPending() {
        List<Comment> result = commentRepository.findAllByPostIdForModeration(1);
        assertThat(result.size()).isGreaterThanOrEqualTo(3);
        assertThat(result).anyMatch(c -> c.getStatus() == Comment.CommentStatus.PENDING);
    }
}
