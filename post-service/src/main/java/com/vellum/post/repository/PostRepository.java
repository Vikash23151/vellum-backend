package com.vellum.post.repository;

import com.vellum.post.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface PostRepository extends JpaRepository<Post, Integer> {

    // Find post by its URL slug
    Optional<Post> findBySlug(String slug);

    // Check if slug already exists (for uniqueness before save)
    boolean existsBySlug(String slug);

    // All posts by a specific author
    List<Post> findByAuthorIdOrderByCreatedAtDesc(Integer authorId);

    // Author's posts filtered by status
    List<Post> findByAuthorIdAndStatusOrderByCreatedAtDesc(
            Integer authorId, Post.PostStatus status);

    /*
     * Pageable: Spring Data pagination.
     * Instead of loading ALL published posts (could be thousands),
     * we load page by page: page 0 = posts 1-20, page 1 = posts 21-40.
     */
    Page<Post> findByStatusOrderByIsFeaturedDescPublishedAtDesc(
            Post.PostStatus status, Pageable pageable);

    // All published posts (no pagination — for caching full feed)
    List<Post> findByStatusOrderByIsFeaturedDescPublishedAtDesc(
            Post.PostStatus status);


    @Query("SELECT p FROM Post p WHERE p.status = 'PUBLISHED' AND (" +
            "LOWER(p.title) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(p.excerpt) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<Post> searchPublishedPosts(@Param("query") String query, Pageable pageable);


    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("UPDATE Post p SET p.viewCount = p.viewCount + 1 WHERE p.postId = :postId")
    void incrementViewCount(@Param("postId") Integer postId);

    // Atomic like count increment
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("UPDATE Post p SET p.likesCount = p.likesCount + 1 WHERE p.postId = :postId")
    void incrementLikesCount(@Param("postId") Integer postId);

    // Atomic like count decrement (unlike)
    @Modifying
    @Transactional
    @Query("UPDATE Post p SET p.likesCount = GREATEST(p.likesCount - 1, 0) WHERE p.postId = :postId")
    void decrementLikesCount(@Param("postId") Integer postId);

    // Feature a post (admin pins to top)
    @Modifying
    @Transactional
    @Query("UPDATE Post p SET p.isFeatured = :featured WHERE p.postId = :postId")
    void setFeatured(@Param("postId") Integer postId, @Param("featured") boolean featured);

    // Count posts per author (for analytics)
    long countByAuthorId(Integer authorId);

    // Count posts by status
    long countByStatus(Post.PostStatus status);

    // Most viewed published posts (for analytics dashboard)
    @Query("SELECT p FROM Post p WHERE p.status = 'PUBLISHED' ORDER BY p.viewCount DESC")
    List<Post> findMostViewedPosts(Pageable pageable);

    // Most liked published posts
    @Query("SELECT p FROM Post p WHERE p.status = 'PUBLISHED' ORDER BY p.likesCount DESC")
    List<Post> findMostLikedPosts(Pageable pageable);
}