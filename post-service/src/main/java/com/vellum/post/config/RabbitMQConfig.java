package com.vellum.post.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
public class RabbitMQConfig {

    // Exchange name — shared across all services
    public static final String EXCHANGE = "vellum.exchange";

    // Queue names — each consuming service has its own queue
    public static final String POST_PUBLISHED_NEWSLETTER_QUEUE   = "vellum.post.published.newsletter";
    public static final String POST_PUBLISHED_NOTIFICATION_QUEUE = "vellum.post.published.notification";

    // Routing keys — labels on messages
    public static final String POST_PUBLISHED_ROUTING_KEY = "post.published";
    public static final String POST_DELETED_ROUTING_KEY   = "post.deleted";

    @Bean
    public TopicExchange vellumExchange() {
        /*
         * durable = true: exchange survives RabbitMQ restart
         * If false and RabbitMQ restarts → exchange disappears → messages lost
         */
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue postPublishedNewsletterQueue() {
        /*
         * durable = true: queue survives RabbitMQ restart
         * If false and RabbitMQ restarts → queue + all unread messages disappear
         */
        return QueueBuilder
                .durable(POST_PUBLISHED_NEWSLETTER_QUEUE)
                .build();
    }

    @Bean
    public Queue postPublishedNotificationQueue() {
        return QueueBuilder
                .durable(POST_PUBLISHED_NOTIFICATION_QUEUE)
                .build();
    }

    @Bean
    public Binding newsletterBinding(Queue postPublishedNewsletterQueue,
                                     TopicExchange vellumExchange) {
        return BindingBuilder
                .bind(postPublishedNewsletterQueue)
                .to(vellumExchange)
                .with(POST_PUBLISHED_ROUTING_KEY);
    }

    @Bean
    public Binding notificationBinding(Queue postPublishedNotificationQueue,
                                       TopicExchange vellumExchange) {
        return BindingBuilder
                .bind(postPublishedNotificationQueue)
                .to(vellumExchange)
                .with(POST_PUBLISHED_ROUTING_KEY);
    }

    /*
     * Jackson2JsonMessageConverter:
     * Converts Java objects → JSON when publishing to RabbitMQ.
     * Converts JSON → Java objects when consuming from RabbitMQ.
     *
     */
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }
}