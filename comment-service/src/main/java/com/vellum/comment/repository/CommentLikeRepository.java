package com.vellum.comment.repository;

import com.vellum.comment.entity.CommentLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CommentLikeRepository extends JpaRepository<CommentLike, Integer> {

    boolean existsByCommentIdAndUserId(Integer commentId, Integer userId);

    Optional<CommentLike> findByCommentIdAndUserId(Integer commentId, Integer userId);

    void deleteByCommentIdAndUserId(Integer commentId, Integer userId);

    long countByCommentId(Integer commentId);
}
