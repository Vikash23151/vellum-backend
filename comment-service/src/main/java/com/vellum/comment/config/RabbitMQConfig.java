package com.vellum.comment.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Profile("!test")
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "vellum.exchange";
    public static final String POST_DELETED_COMMENT_QUEUE = "vellum.post.deleted.comment";
    public static final String POST_DELETED_ROUTING_KEY = "post.deleted";
    public static final String COMMENT_ADDED_ROUTING_KEY = "comment.added";
    public static final String COMMENT_REPLY_ROUTING_KEY = "comment.reply";

    @Bean
    public TopicExchange vellumExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue postDeletedCommentQueue() {
        return QueueBuilder
                .durable(POST_DELETED_COMMENT_QUEUE)
                .build();
    }

    @Bean
    public Binding postDeletedCommentBinding(
            Queue postDeletedCommentQueue,
            TopicExchange vellumExchange) {
        return BindingBuilder
                .bind(postDeletedCommentQueue)
                .to(vellumExchange)
                .with(POST_DELETED_ROUTING_KEY);
    }

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
