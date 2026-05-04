package com.vellum.post.repository;

import com.vellum.post.entity.Post;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("PostRepository Tests")
class PostRepositoryTest {

    @Autowired
    private PostRepository postRepository;

    private Post publishedPost;
    private Post draftPost;

    @BeforeEach
    void setUp() {
        publishedPost = postRepository.save(Post.builder()
                .authorId(1)
                .title("Published Post Title")
                .slug("published-post-title")
                .content("<p>Content here</p>")
                .excerpt("Short excerpt")
                .status(Post.PostStatus.PUBLISHED)
                .readTimeMin(2)
                .publishedAt(LocalDateTime.now())
                .build());

        draftPost = postRepository.save(Post.builder()
                .authorId(1)
                .title("Draft Post Title")
                .slug("draft-post-title")
                .content("<p>Draft content</p>")
                .status(Post.PostStatus.DRAFT)
                .readTimeMin(1)
                .build());
    }

    @Test
    @DisplayName("findBySlug: returns post when slug exists")
    void findBySlug_exists_returnsPost() {
        Optional<Post> result = postRepository.findBySlug("published-post-title");

        assertThat(result).isPresent();
        assertThat(result.get().getTitle()).isEqualTo("Published Post Title");
    }

    @Test
    @DisplayName("findBySlug: returns empty when slug not found")
    void findBySlug_notExists_returnsEmpty() {
        Optional<Post> result = postRepository.findBySlug("non-existent-slug");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("existsBySlug: returns true for existing slug")
    void existsBySlug_exists_returnsTrue() {
        assertThat(postRepository.existsBySlug("published-post-title")).isTrue();
    }

    @Test
    @DisplayName("existsBySlug: returns false for non-existing slug")
    void existsBySlug_notExists_returnsFalse() {
        assertThat(postRepository.existsBySlug("no-such-slug")).isFalse();
    }

    @Test
    @DisplayName("findByAuthorId: returns all posts by author")
    void findByAuthorIdOrderByCreatedAtDesc_returnsAuthorPosts() {
        List<Post> result = postRepository.findByAuthorIdOrderByCreatedAtDesc(1);

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("findByStatus(PUBLISHED): returns only published posts")
    void findByStatus_published_returnsPublishedOnly() {
        Page<Post> result = postRepository
                .findByStatusOrderByIsFeaturedDescPublishedAtDesc(
                        Post.PostStatus.PUBLISHED,
                        PageRequest.of(0, 10)
                );

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getStatus())
                .isEqualTo(Post.PostStatus.PUBLISHED);
    }

    @Test
    @DisplayName("searchPublishedPosts: finds by title keyword")
    void searchPublishedPosts_byTitle_returnsMatching() {
        Page<Post> result = postRepository.searchPublishedPosts(
                "Published",
                PageRequest.of(0, 10)
        );

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTitle())
                .contains("Published");
    }

    @Test
    @DisplayName("searchPublishedPosts: does not return drafts")
    void searchPublishedPosts_noDrafts() {
        Page<Post> result = postRepository.searchPublishedPosts(
                "Draft",
                PageRequest.of(0, 10)
        );

        // Draft post should NOT appear in published search
        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("countByAuthorId: returns correct count")
    void countByAuthorId_returnsCorrectCount() {
        assertThat(postRepository.countByAuthorId(1)).isEqualTo(2);
        assertThat(postRepository.countByAuthorId(99)).isEqualTo(0);
    }

    @Test
    @DisplayName("incrementViewCount: atomically increments view count")
    void incrementViewCount_incrementsCorrectly() {
        int initialCount = publishedPost.getViewCount();

        postRepository.incrementViewCount(publishedPost.getPostId());

        Post updated = postRepository.findById(publishedPost.getPostId()).get();
        assertThat(updated.getViewCount()).isEqualTo(initialCount + 1);
    }

    @Test
    @DisplayName("incrementLikesCount: atomically increments likes count")
    void incrementLikesCount_incrementsCorrectly() {
        postRepository.incrementLikesCount(publishedPost.getPostId());

        Post updated = postRepository.findById(publishedPost.getPostId()).get();
        assertThat(updated.getLikesCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("decrementLikesCount: does not go below zero (GREATEST)")
    void decrementLikesCount_doesNotGoBelowZero() {
        // Start at 0 likes, try to decrement
        postRepository.decrementLikesCount(publishedPost.getPostId());

        Post updated = postRepository.findById(publishedPost.getPostId()).get();
        // GREATEST(count - 1, 0) = GREATEST(-1, 0) = 0
        assertThat(updated.getLikesCount()).isEqualTo(0);
    }
}