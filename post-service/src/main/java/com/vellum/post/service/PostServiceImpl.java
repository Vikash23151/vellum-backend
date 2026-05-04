package com.vellum.post.service;

import com.vellum.post.config.RabbitMQConfig;
import com.vellum.post.dto.*;
import com.vellum.post.entity.Post;
import com.vellum.post.entity.PostLike;
import com.vellum.post.exception.CustomException;
import com.vellum.post.repository.PostLikeRepository;
import com.vellum.post.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostServiceImpl implements PostService {

    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;
    private final RabbitTemplate rabbitTemplate;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${app.post.words-per-minute:200}")
    private int wordsPerMinute;

    @Value("${app.post.excerpt-length:200}")
    private int excerptLength;

    @Value("${app.post.view-session-ttl-hours:24}")
    private int viewSessionTtlHours;

    // CREATE POST
    @Override
    @Transactional
    public PostResponse createPost(CreatePostRequest request, Integer authorId) {
        log.info("Creating post: '{}' by authorId: {}", request.getTitle(), authorId);

        // Generate slug from title
        String slug = generateUniqueSlug(request.getTitle());

        // Compute read time
        int readTime = computeReadTime(request.getContent());

        // Generate or clean excerpt
        String excerpt = generateExcerpt(request.getContent(), request.getExcerpt());

        Post.PostStatus status = request.isPublishImmediately()
                ? Post.PostStatus.PUBLISHED
                : Post.PostStatus.DRAFT;

        Post post = Post.builder()
                .authorId(authorId)
                .title(request.getTitle())
                .slug(slug)
                .content(request.getContent())
                .excerpt(excerpt)
                .featuredImageUrl(request.getFeaturedImageUrl())
                .status(status)
                .readTimeMin(readTime)
                .publishedAt(status == Post.PostStatus.PUBLISHED
                        ? LocalDateTime.now() : null)
                .build();

        Post savedPost = postRepository.save(post);

        // If published immediately → trigger newsletter/notification events
        if (status == Post.PostStatus.PUBLISHED) {
            publishPostEvent(savedPost);
        }

        log.info("Post created: id={} slug={} status={}", savedPost.getPostId(), slug, status);
        return PostResponse.fromEntityWithContent(savedPost);
    }

    // GET POST BY ID
    @Override
    public PostResponse getPostById(Integer postId) {
        Post post = findPostOrThrow(postId);
        return PostResponse.fromEntityWithContent(post);
    }

    // GET POST BY SLUG
    @Override
    public PostResponse getPostBySlug(String slug,
                                      String sessionId,
                                      Integer currentUserId) {
        Post post = postRepository.findBySlug(slug)
                .orElseThrow(() -> new CustomException(
                        "Post not found: " + slug, HttpStatus.NOT_FOUND));

        // Only published posts visible to non-authors
        if (post.getStatus() != Post.PostStatus.PUBLISHED
                && !post.getAuthorId().equals(currentUserId)) {
            throw new CustomException("Post not found", HttpStatus.NOT_FOUND);
        }

        // Track view (unique per session per 24h)
        trackView(post.getPostId(), sessionId);

        PostResponse response = PostResponse.fromEntityWithContent(post);

        // Check if current user liked this post
        if (currentUserId != null) {
            response.setLikedByCurrentUser(
                    postLikeRepository.existsByPostIdAndUserId(post.getPostId(), currentUserId)
            );
        }

        return response;
    }

    // GET PUBLISHED POSTS (FEED)
    @Override
    public Page<PostResponse> getPublishedPosts(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Post> posts  = postRepository
                .findByStatusOrderByIsFeaturedDescPublishedAtDesc(
                        Post.PostStatus.PUBLISHED, pageable);

        return posts.map(PostResponse::fromEntity);
    }

    // GET POSTS BY AUTHOR
    @Override
    public List<PostResponse> getPostsByAuthor(Integer authorId) {
        return postRepository
                .findByAuthorIdOrderByCreatedAtDesc(authorId)
                .stream()
                .map(PostResponse::fromEntity)
                .toList();
    }

    // SEARCH POSTS
    @Override
    public Page<PostResponse> searchPosts(String query, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return postRepository
                .searchPublishedPosts(query, pageable)
                .map(PostResponse::fromEntity);
    }

    // UPDATE POST
    @Override
    @Transactional
    public PostResponse updatePost(Integer postId,
                                   UpdatePostRequest request,
                                   Integer requestingUserId) {
        Post post = findPostOrThrow(postId);

        // Only author or admin can update
        validateOwnership(post, requestingUserId);

        // Update only provided (non-null) fields
        if (request.getTitle() != null) {
            post.setTitle(request.getTitle());
            // Regenerate slug only if title changes and post is still DRAFT
            // For PUBLISHED posts: don't change slug (breaks existing URLs)
            if (post.getStatus() == Post.PostStatus.DRAFT) {
                post.setSlug(generateUniqueSlug(request.getTitle()));
            }
        }
        if (request.getContent() != null) {
            post.setContent(request.getContent());
            post.setReadTimeMin(computeReadTime(request.getContent()));
        }
        if (request.getExcerpt() != null) {
            post.setExcerpt(request.getExcerpt());
        } else if (request.getContent() != null) {
            // Auto-regenerate excerpt from new content
            post.setExcerpt(generateExcerpt(request.getContent(), null));
        }
        if (request.getFeaturedImageUrl() != null) {
            post.setFeaturedImageUrl(request.getFeaturedImageUrl());
        }

        Post updated = postRepository.save(post);
        log.info("Post updated: id={}", postId);
        return PostResponse.fromEntityWithContent(updated);
    }

    // PUBLISH POST
    @Override
    @Transactional
    public PostResponse publishPost(Integer postId, Integer requestingUserId) {
        Post post = findPostOrThrow(postId);
        validateOwnership(post, requestingUserId);

        if (post.getContent() == null || post.getContent().isBlank()) {
            throw new CustomException(
                    "Cannot publish a post with empty content", HttpStatus.BAD_REQUEST);
        }

        if (post.getStatus() == Post.PostStatus.PUBLISHED) {
            throw new CustomException("Post is already published", HttpStatus.BAD_REQUEST);
        }

        post.setStatus(Post.PostStatus.PUBLISHED);

        // Set publishedAt only on FIRST publish
        if (post.getPublishedAt() == null) {
            post.setPublishedAt(LocalDateTime.now());
        }

        Post published = postRepository.save(post);

        // Notify newsletter and notification services asynchronously
        publishPostEvent(published);

        log.info("Post published: id={} slug={}", postId, post.getSlug());
        return PostResponse.fromEntityWithContent(published);
    }

    // UNPUBLISH POST
    @Override
    @Transactional
    public PostResponse unpublishPost(Integer postId, Integer requestingUserId) {
        Post post = findPostOrThrow(postId);
        validateOwnership(post, requestingUserId);

        post.setStatus(Post.PostStatus.UNPUBLISHED);
        Post updated = postRepository.save(post);

        log.info("Post unpublished: id={}", postId);
        return PostResponse.fromEntityWithContent(updated);
    }

    // DELETE POST
    @Override
    @Transactional
    public void deletePost(Integer postId, Integer requestingUserId) {
        Post post = findPostOrThrow(postId);
        validateOwnership(post, requestingUserId);

        postRepository.delete(post);

        // Publish delete event so comment-service deletes comments too
        Map<String, Object> event = new HashMap<>();
        event.put("postId", postId);
        event.put("authorId", post.getAuthorId());

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE,
                RabbitMQConfig.POST_DELETED_ROUTING_KEY,
                event
        );

        log.info("Post deleted: id={}", postId);
    }

    // LIKE POST
    @Override
    @Transactional
    public void likePost(Integer postId, Integer userId) {
        findPostOrThrow(postId);

        if (postLikeRepository.existsByPostIdAndUserId(postId, userId)) {
            throw new CustomException("You already liked this post", HttpStatus.CONFLICT);
        }

        PostLike like = PostLike.builder()
                .postId(postId)
                .userId(userId)
                .build();

        postLikeRepository.save(like);

        // Atomic increment on DB level (thread-safe)
        postRepository.incrementLikesCount(postId);

        log.debug("Post liked: postId={} userId={}", postId, userId);
    }

    // UNLIKE POST
    @Override
    @Transactional
    public void unlikePost(Integer postId, Integer userId) {
        findPostOrThrow(postId);

        if (!postLikeRepository.existsByPostIdAndUserId(postId, userId)) {
            throw new CustomException("You haven't liked this post", HttpStatus.BAD_REQUEST);
        }

        postLikeRepository.deleteByPostIdAndUserId(postId, userId);
        postRepository.decrementLikesCount(postId);

        log.debug("Post unliked: postId={} userId={}", postId, userId);
    }

    // IS LIKED BY USER
    @Override
    public boolean isLikedByUser(Integer postId, Integer userId) {
        return postLikeRepository.existsByPostIdAndUserId(postId, userId);
    }

    // FEATURE POST (ADMIN)
    @Override
    @Transactional
    public void featurePost(Integer postId, boolean featured) {
        findPostOrThrow(postId);
        postRepository.setFeatured(postId, featured);
        log.info("⭐ Post featured={}: id={}", featured, postId);
    }

    // GET POST COUNT
    @Override
    public long getPostCount() {
        return postRepository.count();
    }

    // MOST VIEWED POSTS
    @Override
    public List<PostResponse> getMostViewedPosts(int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return postRepository.findMostViewedPosts(pageable)
                .stream()
                .map(PostResponse::fromEntity)
                .toList();
    }


    // PRIVATE HELPER METHODS

    private String generateUniqueSlug(String title) {
        String baseSlug = title
                .toLowerCase()
                .trim()
                .replaceAll("[^a-z0-9\\s-]", "")   // keep letters, numbers, spaces, hyphens
                .replaceAll("\\s+", "-")             // spaces → hyphens
                .replaceAll("-+", "-")               // multiple hyphens → single
                .replaceAll("^-|-$", "");            // trim leading/trailing hyphens

        // Limit length
        if (baseSlug.length() > 300) {
            baseSlug = baseSlug.substring(0, 300);
        }

        // Ensure uniqueness
        String slug = baseSlug;
        int counter = 2;

        while (postRepository.existsBySlug(slug)) {
            slug = baseSlug + "-" + counter;
            counter++;
        }

        return slug;
    }


    private int computeReadTime(String htmlContent) {
        if (htmlContent == null || htmlContent.isBlank()) {
            return 1;
        }

        // Jsoup.parse().text() strips all HTML tags
        String plainText = Jsoup.parse(htmlContent).text();

        // Count words (split on whitespace)
        long wordCount = Arrays.stream(plainText.split("\\s+"))
                .filter(w -> !w.isBlank())
                .count();

        int minutes = (int) Math.ceil((double) wordCount / wordsPerMinute);
        return Math.max(1, minutes); // minimum 1 minute
    }


    private String generateExcerpt(String htmlContent, String manualExcerpt) {
        if (manualExcerpt != null && !manualExcerpt.isBlank()) {
            return Jsoup.parse(manualExcerpt).text();
        }

        if (htmlContent == null || htmlContent.isBlank()) {
            return "";
        }

        String plainText = Jsoup.parse(htmlContent).text();

        if (plainText.length() <= excerptLength) {
            return plainText;
        }

        // Cut at word boundary (don't cut in middle of word)
        String truncated = plainText.substring(0, excerptLength);
        int lastSpace    = truncated.lastIndexOf(' ');
        if (lastSpace > 0) {
            truncated = truncated.substring(0, lastSpace);
        }

        return truncated + "...";
    }


    private void trackView(Integer postId, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = "anonymous:" + UUID.randomUUID();
        }

        String redisKey = "view:" + postId + ":" + sessionId;

        /*
         * setIfAbsent (Redis SETNX = SET if Not eXists):
         * Returns true if key was SET (new view)
         * Returns false if key already existed (already viewed)
         *
         * This is atomic — no race condition possible.
         */
        Boolean isNewView = redisTemplate.opsForValue()
                .setIfAbsent(redisKey, "1", viewSessionTtlHours, TimeUnit.HOURS);

        if (Boolean.TRUE.equals(isNewView)) {
            // Atomic DB increment
            postRepository.incrementViewCount(postId);
            log.debug("View counted: postId={} session={}", postId, sessionId);
        }
    }


    private void publishPostEvent(Post post) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("postId",    post.getPostId());
            event.put("authorId",  post.getAuthorId());
            event.put("title",     post.getTitle());
            event.put("slug",      post.getSlug());
            event.put("excerpt",   post.getExcerpt());
            event.put("publishedAt", post.getPublishedAt().toString());

            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE,
                    RabbitMQConfig.POST_PUBLISHED_ROUTING_KEY,
                    event
            );

            log.info("Post published event sent: postId={}", post.getPostId());
        } catch (Exception e) {
            // Don't fail the publish operation if messaging fails
            log.error("Failed to send post published event: {}", e.getMessage());
        }
    }

    private Post findPostOrThrow(Integer postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new CustomException(
                        "Post not found: " + postId, HttpStatus.NOT_FOUND));
    }


    private void validateOwnership(Post post, Integer requestingUserId) {
        if (!post.getAuthorId().equals(requestingUserId)) {
            throw new CustomException(
                    "You don't have permission to modify this post",
                    HttpStatus.FORBIDDEN
            );
        }
    }
}