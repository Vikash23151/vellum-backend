package com.vellum.newsletter.service;

import com.vellum.newsletter.dto.*;
import com.vellum.newsletter.entity.Subscriber;

import java.util.List;
import java.util.Map;

public interface NewsletterService {

    SubscriberResponse subscribe(SubscribeRequest request);

    SubscriberResponse confirmSubscription(String token);

    void unsubscribe(String token);

    SubscriberResponse getSubscriberByEmail(String email);

    List<SubscriberResponse> getAllSubscribers();

    List<SubscriberResponse> getActiveSubscribers();

    void sendNewsletter(SendNewsletterRequest request);

    void sendNewPostNotification(Map<String, Object> postEvent);

    SubscriberResponse updatePreferences(Integer subscriberId,
                                         String preferences);

    Map<String, Long> getStats();
}