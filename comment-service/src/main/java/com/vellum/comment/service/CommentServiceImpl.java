package com.vellum.comment.service;

import com.vellum.comment.config.RabbitMQConfig;
import com.vellum.comment.dto.*;
import com.vellum.comment.entity.Comment;
import com.vellum.comment.entity.CommentLike;
import com.vellum.comment.exception.CustomException;
import com.vellum.comment.repository.CommentLikeRepository;
import com.vellum.comment.repository.CommentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommentServiceImpl implements CommentService {

    private final CommentRepository commentRepository;
    private final CommentLikeRepository commentLikeRepository;
    private final RabbitTemplate rabbitTemplate;

    @Value("${app.comment.edit-window-minutes:15}")
    private int editWindowMinutes;

    @Value("${app.comment.moderation-mode:AUTO_APPROVE}")
    private String moderationMode;

    @Override
    @Transactional
    public CommentResponse addComment(AddCommentRequest request, Integer authorId) {
        if (request.getParentCommentId() != null) {
            Comment parent = commentRepository
                    .findById(request.getParentCommentId())
                    .orElseThrow(() -> new CustomException(
                            "Parent comment not found",
                            HttpStatus.NOT_FOUND));

            if (parent.getParentCommentId() != null) {
                throw new CustomException(
                        "Cannot reply to a reply. Maximum thread depth is 2.",
                        HttpStatus.BAD_REQUEST);
            }

            if (parent.getStatus() == Comment.CommentStatus.DELETED) {
                throw new CustomException(
                        "Cannot reply to a deleted comment",
                        HttpStatus.BAD_REQUEST);
            }
        }

        Comment.CommentStatus initialStatus =
                "REQUIRE_MODERATION".equals(moderationMode)
                        ? Comment.CommentStatus.PENDING
                        : Comment.CommentStatus.APPROVED;

        Comment comment = Comment.builder()
                .postId(request.getPostId())
                .authorId(authorId)
                .parentCommentId(request.getParentCommentId())
                .content(request.getContent())
                .status(initialStatus)
                .build();

        Comment saved = commentRepository.save(comment);
        publishCommentEvent(saved, request.getParentCommentId() != null);
        return CommentResponse.fromEntity(saved);
    }

    @Override
    public CommentResponse getCommentById(Integer commentId) {
        Comment comment = findCommentOrThrow(commentId);
        return CommentResponse.fromEntity(comment);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommentResponse> getCommentsByPost(Integer postId, Integer currentUserId) {
        List<Comment> topLevelComments = commentRepository.findTopLevelByPostId(postId);

        return topLevelComments.stream()
                .map(comment -> {
                    CommentResponse response = CommentResponse.fromEntity(comment);
                    enrichCommentResponse(response, comment, currentUserId);

                    List<Comment> replies = commentRepository.findRepliesByParentId(comment.getCommentId());

                    List<CommentResponse> replyResponses = replies.stream()
                            .map(reply -> {
                                CommentResponse replyResponse = CommentResponse.fromEntity(reply);
                                enrichCommentResponse(replyResponse, reply, currentUserId);
                                return replyResponse;
                            })
                            .collect(Collectors.toList());

                    response.setReplies(replyResponses);
                    return response;
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommentResponse> getReplies(Integer parentCommentId, Integer currentUserId) {
        findCommentOrThrow(parentCommentId);
        return commentRepository.findRepliesByParentId(parentCommentId)
                .stream()
                .map(reply -> {
                    CommentResponse response = CommentResponse.fromEntity(reply);
                    enrichCommentResponse(response, reply, currentUserId);
                    return response;
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommentResponse> getCommentsForModeration(Integer postId) {
        return commentRepository.findAllByPostIdForModeration(postId)
                .stream()
                .map(CommentResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public CommentResponse updateComment(Integer commentId, UpdateCommentRequest request, Integer requestingUserId) {
        Comment comment = findCommentOrThrow(commentId);

        if (!comment.getAuthorId().equals(requestingUserId)) {
            throw new CustomException("You can only edit your own comments", HttpStatus.FORBIDDEN);
        }

        if (comment.getStatus() == Comment.CommentStatus.DELETED) {
            throw new CustomException("Cannot edit a deleted comment", HttpStatus.BAD_REQUEST);
        }

        long minutesSincePosted = ChronoUnit.MINUTES.between(comment.getCreatedAt(), LocalDateTime.now());
        if (minutesSincePosted > editWindowMinutes) {
            throw new CustomException(
                    String.format(
                            "Comments can only be edited within %d minutes of posting. This comment was posted %d minutes ago.",
                            editWindowMinutes, minutesSincePosted),
                    HttpStatus.BAD_REQUEST);
        }

        comment.setContent(request.getContent());
        comment.setEdited(true);
        return CommentResponse.fromEntity(commentRepository.save(comment));
    }

    @Override
    @Transactional
    public void deleteComment(Integer commentId, Integer requestingUserId, String requestingUserRole) {
        Comment comment = findCommentOrThrow(commentId);
        boolean isAdmin = "ADMIN".equals(requestingUserRole);
        boolean isOwner = comment.getAuthorId().equals(requestingUserId);

        if (!isOwner && !isAdmin) {
            throw new CustomException("You don't have permission to delete this comment", HttpStatus.FORBIDDEN);
        }

        commentRepository.softDeleteComment(commentId);
        commentRepository.softDeleteRepliesByParentId(commentId);
    }

    @Override
    @Transactional
    public CommentResponse approveComment(Integer commentId, Integer requestingUserId, String requestingUserRole) {
        Comment comment = findCommentOrThrow(commentId);

        if (!"ADMIN".equals(requestingUserRole) && !"AUTHOR".equals(requestingUserRole)) {
            throw new CustomException("Only authors and admins can approve comments", HttpStatus.FORBIDDEN);
        }

        if (comment.getStatus() != Comment.CommentStatus.PENDING) {
            throw new CustomException("Only PENDING comments can be approved", HttpStatus.BAD_REQUEST);
        }

        comment.setStatus(Comment.CommentStatus.APPROVED);
        return CommentResponse.fromEntity(commentRepository.save(comment));
    }

    @Override
    @Transactional
    public CommentResponse rejectComment(Integer commentId, Integer requestingUserId, String requestingUserRole) {
        Comment comment = findCommentOrThrow(commentId);

        if (!"ADMIN".equals(requestingUserRole) && !"AUTHOR".equals(requestingUserRole)) {
            throw new CustomException("Only authors and admins can reject comments", HttpStatus.FORBIDDEN);
        }

        if (comment.getStatus() != Comment.CommentStatus.PENDING) {
            throw new CustomException("Only PENDING comments can be rejected", HttpStatus.BAD_REQUEST);
        }

        comment.setStatus(Comment.CommentStatus.REJECTED);
        return CommentResponse.fromEntity(commentRepository.save(comment));
    }

    @Override
    @Transactional
    public void likeComment(Integer commentId, Integer userId) {
        findCommentOrThrow(commentId);

        if (commentLikeRepository.existsByCommentIdAndUserId(commentId, userId)) {
            throw new CustomException("You already liked this comment", HttpStatus.CONFLICT);
        }

        CommentLike like = CommentLike.builder().commentId(commentId).userId(userId).build();
        commentLikeRepository.save(like);
        commentRepository.incrementLikesCount(commentId);
    }

    @Override
    @Transactional
    public void unlikeComment(Integer commentId, Integer userId) {
        findCommentOrThrow(commentId);

        if (!commentLikeRepository.existsByCommentIdAndUserId(commentId, userId)) {
            throw new CustomException("You haven't liked this comment", HttpStatus.BAD_REQUEST);
        }

        commentLikeRepository.deleteByCommentIdAndUserId(commentId, userId);
        commentRepository.decrementLikesCount(commentId);
    }

    @Override
    public long getCommentCount(Integer postId) {
        return commentRepository.countApprovedByPostId(postId);
    }

    @Override
    @RabbitListener(queues = RabbitMQConfig.POST_DELETED_COMMENT_QUEUE)
    @Transactional
    public void deleteAllCommentsForPost(Integer postId) {
        commentRepository.deleteAllByPostId(postId);
    }

    @RabbitListener(queues = RabbitMQConfig.POST_DELETED_COMMENT_QUEUE)
    @Transactional
    public void handlePostDeletedEvent(Map<String, Object> payload) {
        Integer postId = (Integer) payload.get("postId");
        commentRepository.deleteAllByPostId(postId);
    }

    private Comment findCommentOrThrow(Integer commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> new CustomException("Comment not found: " + commentId, HttpStatus.NOT_FOUND));
    }

    private void enrichCommentResponse(CommentResponse response, Comment comment, Integer currentUserId) {
        if (currentUserId != null) {
            response.setLikedByCurrentUser(
                    commentLikeRepository.existsByCommentIdAndUserId(comment.getCommentId(), currentUserId));
            response.setOwner(comment.getAuthorId().equals(currentUserId));
        }
    }

    private void publishCommentEvent(Comment comment, boolean isReply) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("commentId", comment.getCommentId());
            event.put("postId", comment.getPostId());
            event.put("authorId", comment.getAuthorId());
            event.put("parentCommentId", comment.getParentCommentId());
            event.put("isReply", isReply);
            event.put("content", comment.getContent().substring(0, Math.min(100, comment.getContent().length())));

            String routingKey = isReply
                    ? RabbitMQConfig.COMMENT_REPLY_ROUTING_KEY
                    : RabbitMQConfig.COMMENT_ADDED_ROUTING_KEY;

            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, routingKey, event);
        } catch (Exception e) {
            log.error("Failed to publish comment event: {}", e.getMessage());
        }
    }
}
