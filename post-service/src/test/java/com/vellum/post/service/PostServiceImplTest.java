package com.vellum.post.service;

import com.vellum.post.dto.CreatePostRequest;
import com.vellum.post.dto.PostResponse;
import com.vellum.post.entity.Post;
import com.vellum.post.exception.CustomException;
import com.vellum.post.repository.PostLikeRepository;
import com.vellum.post.repository.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PostServiceImpl Unit Tests")
class PostServiceImplTest {

    @Mock private PostRepository postRepository;
    @Mock private PostLikeRepository postLikeRepository;
    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private PostServiceImpl postService;

    private Post testPost;

    @BeforeEach
    void setUp() {
        // Inject @Value fields manually
        ReflectionTestUtils.setField(postService, "wordsPerMinute", 200);
        ReflectionTestUtils.setField(postService, "excerptLength", 200);
        ReflectionTestUtils.setField(postService, "viewSessionTtlHours", 24);

        testPost = Post.builder()
                .postId(1)
                .authorId(42)
                .title("Test Post Title")
                .slug("test-post-title")
                .content("<p>This is test content for the post.</p>")
                .excerpt("This is test content")
                .status(Post.PostStatus.DRAFT)
                .readTimeMin(1)
                .viewCount(0)
                .likesCount(0)
                .build();
    }

    // createPost() tests

    @Test
    @DisplayName("createPost: success - creates DRAFT by default")
    void createPost_success_createsDraft() {
        CreatePostRequest request = new CreatePostRequest();
        request.setTitle("My New Post");
        request.setContent("<p>Hello world content here for testing.</p>");
        request.setPublishImmediately(false);

        when(postRepository.existsBySlug(anyString())).thenReturn(false);
        when(postRepository.save(any(Post.class))).thenReturn(testPost);

        PostResponse result = postService.createPost(request, 42);

        assertThat(result).isNotNull();
        verify(postRepository, times(1)).save(any(Post.class));
        // RabbitMQ should NOT be called for drafts
        verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(String.class), any(Object.class));
    }

    @Test
    @DisplayName("createPost: publishImmediately=true creates PUBLISHED post")
    void createPost_publishImmediately_createsPublished() {
        CreatePostRequest request = new CreatePostRequest();
        request.setTitle("Immediate Post");
        request.setContent("<p>Some meaningful content to publish right away.</p>");
        request.setPublishImmediately(true);

        Post publishedPost = Post.builder()
                .postId(2)
                .authorId(42)
                .title("Immediate Post")
                .slug("immediate-post")
                .status(Post.PostStatus.PUBLISHED)
                .publishedAt(LocalDateTime.now())
                .content("<p>Some meaningful content to publish right away.</p>")
                .readTimeMin(1)
                .build();

        when(postRepository.existsBySlug(anyString())).thenReturn(false);
        when(postRepository.save(any(Post.class))).thenReturn(publishedPost);

        PostResponse result = postService.createPost(request, 42);

        assertThat(result.getStatus()).isEqualTo("PUBLISHED");
        // RabbitMQ SHOULD be called when publishing
        verify(rabbitTemplate, times(1))
                .convertAndSend(anyString(), anyString(), any(Object.class));
    }

    // getPostById() tests

    @Test
    @DisplayName("getPostById: returns post when found")
    void getPostById_found_returnsPost() {
        when(postRepository.findById(1)).thenReturn(Optional.of(testPost));

        PostResponse result = postService.getPostById(1);

        assertThat(result).isNotNull();
        assertThat(result.getPostId()).isEqualTo(1);
        assertThat(result.getTitle()).isEqualTo("Test Post Title");
    }

    @Test
    @DisplayName("getPostById: throws 404 when not found")
    void getPostById_notFound_throws404() {
        when(postRepository.findById(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.getPostById(999))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    // getPublishedPosts() tests

    @Test
    @DisplayName("getPublishedPosts: returns paginated published posts")
    void getPublishedPosts_returnsPaginatedResults() {
        Post post2 = Post.builder()
                .postId(2).authorId(1)
                .title("Another Post").slug("another-post")
                .status(Post.PostStatus.PUBLISHED)
                .readTimeMin(1).viewCount(0).likesCount(0)
                .build();

        var pageResult = new PageImpl<>(
                List.of(testPost, post2),
                PageRequest.of(0, 20),
                2
        );

        when(postRepository.findByStatusOrderByIsFeaturedDescPublishedAtDesc(
                eq(Post.PostStatus.PUBLISHED), any()
        )).thenReturn(pageResult);

        var result = postService.getPublishedPosts(0, 20);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    // publishPost() tests

    @Test
    @DisplayName("publishPost: success transitions DRAFT to PUBLISHED")
    void publishPost_success_transitionsToDraft() {
        when(postRepository.findById(1)).thenReturn(Optional.of(testPost));
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> {
            Post saved = inv.getArgument(0);
            // Verify status was set to PUBLISHED before save
            assertThat(saved.getStatus()).isEqualTo(Post.PostStatus.PUBLISHED);
            return saved;
        });

        PostResponse result = postService.publishPost(1, 42);

        assertThat(result.getStatus()).isEqualTo("PUBLISHED");
        verify(rabbitTemplate, times(1))
                .convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    @DisplayName("publishPost: fails when content is empty")
    void publishPost_emptyContent_throwsBadRequest() {
        Post emptyPost = Post.builder()
                .postId(3)
                .authorId(42)
                .title("Empty Post")
                .slug("empty-post")
                .content("")  // empty content
                .status(Post.PostStatus.DRAFT)
                .build();

        when(postRepository.findById(3)).thenReturn(Optional.of(emptyPost));

        assertThatThrownBy(() -> postService.publishPost(3, 42))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("publishPost: throws 403 when non-author tries to publish")
    void publishPost_nonAuthor_throwsForbidden() {
        when(postRepository.findById(1)).thenReturn(Optional.of(testPost));
        // testPost.authorId = 42, but requesting userId = 99

        assertThatThrownBy(() -> postService.publishPost(1, 99))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getStatus())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    // likePost() tests

    @Test
    @DisplayName("likePost: success adds like and increments count")
    void likePost_success_addsLike() {
        when(postRepository.findById(1)).thenReturn(Optional.of(testPost));
        when(postLikeRepository.existsByPostIdAndUserId(1, 10)).thenReturn(false);

        postService.likePost(1, 10);

        verify(postLikeRepository, times(1)).save(any());
        verify(postRepository, times(1)).incrementLikesCount(1);
    }

    @Test
    @DisplayName("likePost: throws 409 when already liked")
    void likePost_alreadyLiked_throwsConflict() {
        when(postRepository.findById(1)).thenReturn(Optional.of(testPost));
        when(postLikeRepository.existsByPostIdAndUserId(1, 10)).thenReturn(true);

        assertThatThrownBy(() -> postService.likePost(1, 10))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getStatus())
                        .isEqualTo(HttpStatus.CONFLICT));

        verify(postLikeRepository, never()).save(any());
    }

    // deletePost() tests

    @Test
    @DisplayName("deletePost: success deletes post and sends event")
    void deletePost_success_deletesAndSendsEvent() {
        when(postRepository.findById(1)).thenReturn(Optional.of(testPost));

        postService.deletePost(1, 42);  // authorId = 42 matches

        verify(postRepository, times(1)).delete(testPost);
        verify(rabbitTemplate, times(1))
                .convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    @DisplayName("deletePost: throws 403 when non-owner tries to delete")
    void deletePost_nonOwner_throwsForbidden() {
        when(postRepository.findById(1)).thenReturn(Optional.of(testPost));

        assertThatThrownBy(() -> postService.deletePost(1, 99))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getStatus())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        verify(postRepository, never()).delete(any());
    }
}