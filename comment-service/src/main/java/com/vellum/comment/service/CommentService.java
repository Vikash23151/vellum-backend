package com.vellum.comment.service;

import com.vellum.comment.dto.*;

import java.util.List;

public interface CommentService {

    CommentResponse addComment(AddCommentRequest request,
                               Integer authorId);

    CommentResponse getCommentById(Integer commentId);

    List<CommentResponse> getCommentsByPost(Integer postId,
                                            Integer currentUserId);

    List<CommentResponse> getReplies(Integer parentCommentId,
                                     Integer currentUserId);

    List<CommentResponse> getCommentsForModeration(Integer postId);

    CommentResponse updateComment(Integer commentId,
                                  UpdateCommentRequest request,
                                  Integer requestingUserId);

    void deleteComment(Integer commentId,
                       Integer requestingUserId,
                       String requestingUserRole);

    CommentResponse approveComment(Integer commentId,
                                   Integer requestingUserId,
                                   String requestingUserRole);

    CommentResponse rejectComment(Integer commentId,
                                  Integer requestingUserId,
                                  String requestingUserRole);

    void likeComment(Integer commentId, Integer userId);

    void unlikeComment(Integer commentId, Integer userId);

    long getCommentCount(Integer postId);

    void deleteAllCommentsForPost(Integer postId);
}
