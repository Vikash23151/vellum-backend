package com.vellum.post.service;

import com.vellum.post.dto.*;
import com.vellum.post.entity.Post;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Optional;

public interface PostService {

    PostResponse createPost(CreatePostRequest request, Integer authorId);

    PostResponse getPostById(Integer postId);

    PostResponse getPostBySlug(String slug, String sessionId, Integer currentUserId);

    Page<PostResponse> getPublishedPosts(int page, int size);

    List<PostResponse> getPostsByAuthor(Integer authorId);

    Page<PostResponse> searchPosts(String query, int page, int size);

    PostResponse updatePost(Integer postId, UpdatePostRequest request, Integer requestingUserId);

    PostResponse publishPost(Integer postId, Integer requestingUserId);

    PostResponse unpublishPost(Integer postId, Integer requestingUserId);

    void deletePost(Integer postId, Integer requestingUserId);

    void likePost(Integer postId, Integer userId);

    void unlikePost(Integer postId, Integer userId);

    boolean isLikedByUser(Integer postId, Integer userId);

    void featurePost(Integer postId, boolean featured);

    long getPostCount();

    List<PostResponse> getMostViewedPosts(int limit);
}