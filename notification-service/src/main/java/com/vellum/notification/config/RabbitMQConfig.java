package com.vellum.notification.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "vellum.exchange";

    // Queue for post published notifications
    public static final String POST_PUBLISHED_NOTIFICATION_QUEUE =
            "vellum.post.published.notification";

    // Queue for comment notifications (added + replies)
    public static final String COMMENT_NOTIFICATION_QUEUE =
            "vellum.comment.notification";

    // Routing keys
    public static final String POST_PUBLISHED_KEY = "post.published";
    public static final String COMMENT_ADDED_KEY  = "comment.added";
    public static final String COMMENT_REPLY_KEY  = "comment.reply";

    // Wildcard pattern: matches comment.added AND comment.reply
    public static final String COMMENT_WILDCARD   = "comment.*";

    @Bean
    public TopicExchange vellumExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue postPublishedNotificationQueue() {
        return QueueBuilder
                .durable(POST_PUBLISHED_NOTIFICATION_QUEUE)
                .build();
    }

    @Bean
    public Queue commentNotificationQueue() {
        return QueueBuilder
                .durable(COMMENT_NOTIFICATION_QUEUE)
                .build();
    }

    @Bean
    public Binding postPublishedNotificationBinding(
            Queue postPublishedNotificationQueue,
            TopicExchange vellumExchange) {
        return BindingBuilder
                .bind(postPublishedNotificationQueue)
                .to(vellumExchange)
                .with(POST_PUBLISHED_KEY);
    }

    /*
     * Wildcard binding: "comment.*" matches:
     * - "comment.added"  (new comment)
     * - "comment.reply"  (reply to comment)
     *
     * Both are routed to the same queue.
     * Service differentiates them by the "isReply" field
     * in the message payload.
     */
    @Bean
    public Binding commentNotificationBinding(
            Queue commentNotificationQueue,
            TopicExchange vellumExchange) {
        return BindingBuilder
                .bind(commentNotificationQueue)
                .to(vellumExchange)
                .with(COMMENT_WILDCARD);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(
            ConnectionFactory connectionFactory) {
        RabbitTemplate template =
                new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }
}