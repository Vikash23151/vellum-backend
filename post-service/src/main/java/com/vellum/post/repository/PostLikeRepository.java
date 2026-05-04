package com.vellum.post.repository;

import com.vellum.post.entity.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PostLikeRepository extends JpaRepository<PostLike, Integer> {

    // Check if user already liked a post
    boolean existsByPostIdAndUserId(Integer postId, Integer userId);

    // Find the like record (needed for unlike — to delete it)
    Optional<PostLike> findByPostIdAndUserId(Integer postId, Integer userId);

    // Remove a like record
    void deleteByPostIdAndUserId(Integer postId, Integer userId);

    // Count total likes for a post (cross-check with denormalized count)
    long countByPostId(Integer postId);
}