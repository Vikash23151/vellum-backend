package com.vellum.newsletter.dto;

import com.vellum.newsletter.entity.Subscriber;
import lombok.*;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriberResponse {

    private Integer subscriberId;
    private String  email;
    private Integer userId;
    private String  fullName;
    private String  status;
    private String  preferences;
    private LocalDateTime subscribedAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime unsubscribedAt;

    public static SubscriberResponse fromEntity(Subscriber s) {
        return SubscriberResponse.builder()
                .subscriberId(s.getSubscriberId())
                .email(s.getEmail())
                .userId(s.getUserId())
                .fullName(s.getFullName())
                .status(s.getStatus().name())
                .preferences(s.getPreferences())
                .subscribedAt(s.getSubscribedAt())
                .confirmedAt(s.getConfirmedAt())
                .unsubscribedAt(s.getUnsubscribedAt())
                .build();
    }
}