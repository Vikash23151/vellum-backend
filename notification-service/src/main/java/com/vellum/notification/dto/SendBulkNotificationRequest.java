package com.vellum.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class SendBulkNotificationRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 300, message = "Title too long")
    private String title;

    @Size(max = 2000, message = "Message too long")
    private String message;

    /*
     * recipientIds: specific users to notify.
     * null or empty = send to ALL users (system broadcast).
     * Non-empty = notify only these specific users.
     *
     * Use cases:
     * - null: "Server maintenance tonight" → all users
     * - [1,2,3]: "Your account has been verified" → specific users
     */
    private List<Integer> recipientIds;

    /*
     * targetRole: notify all users of a specific role.
     * "AUTHOR" = notify all authors
     * "ADMIN"  = notify all admins
     * null     = no role filter (use recipientIds or all)
     */
    private String targetRole;
}