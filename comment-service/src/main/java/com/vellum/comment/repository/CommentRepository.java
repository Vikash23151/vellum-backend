package com.vellum.comment.repository;

import com.vellum.comment.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Integer> {

    @Query("SELECT c FROM Comment c WHERE c.postId = :postId " +
           "AND c.parentCommentId IS NULL " +
           "AND c.status = 'APPROVED' " +
           "ORDER BY c.createdAt ASC")
    List<Comment> findTopLevelByPostId(@Param("postId") Integer postId);

    @Query("SELECT c FROM Comment c WHERE c.postId = :postId " +
           "AND c.status != 'DELETED' " +
           "ORDER BY c.createdAt ASC")
    List<Comment> findAllByPostIdForModeration(@Param("postId") Integer postId);

    @Query("SELECT c FROM Comment c WHERE c.parentCommentId = :parentId " +
           "AND c.status = 'APPROVED' " +
           "ORDER BY c.createdAt ASC")
    List<Comment> findRepliesByParentId(@Param("parentId") Integer parentId);

    @Query("SELECT COUNT(c) FROM Comment c WHERE c.postId = :postId " +
           "AND c.status = 'APPROVED'")
    long countApprovedByPostId(@Param("postId") Integer postId);

    List<Comment> findByAuthorIdAndStatusNotOrderByCreatedAtDesc(
            Integer authorId, Comment.CommentStatus status);

    List<Comment> findByPostIdAndStatusOrderByCreatedAtAsc(
            Integer postId, Comment.CommentStatus status);

    List<Comment> findByStatusOrderByCreatedAtAsc(Comment.CommentStatus status);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Comment c SET c.status = 'DELETED', " +
           "c.content = 'This comment has been deleted' " +
           "WHERE c.commentId = :commentId")
    void softDeleteComment(@Param("commentId") Integer commentId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Comment c SET c.status = 'DELETED', " +
           "c.content = 'This comment has been deleted' " +
           "WHERE c.parentCommentId = :parentCommentId")
    void softDeleteRepliesByParentId(@Param("parentCommentId") Integer parentCommentId);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM Comment c WHERE c.postId = :postId")
    void deleteAllByPostId(@Param("postId") Integer postId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Comment c SET c.likesCount = c.likesCount + 1 " +
           "WHERE c.commentId = :commentId")
    void incrementLikesCount(@Param("commentId") Integer commentId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Comment c SET c.likesCount = GREATEST(c.likesCount - 1, 0) " +
           "WHERE c.commentId = :commentId")
    void decrementLikesCount(@Param("commentId") Integer commentId);
}
