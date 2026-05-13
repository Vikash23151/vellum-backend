package com.vellum.newsletter.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@ConditionalOnProperty(name = "spring.rabbitmq.enabled", havingValue = "true", matchIfMissing = true)
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "vellum.exchange";

    public static final String POST_PUBLISHED_NEWSLETTER_QUEUE =
            "vellum.post.published.newsletter";

    public static final String POST_PUBLISHED_ROUTING_KEY =
            "post.published";

    @Bean
    public TopicExchange vellumExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue postPublishedNewsletterQueue() {
        return QueueBuilder
                .durable(POST_PUBLISHED_NEWSLETTER_QUEUE)
                .build();
    }

    @Bean
    public Binding newsletterBinding(
            Queue postPublishedNewsletterQueue,
            TopicExchange vellumExchange) {
        return BindingBuilder
                .bind(postPublishedNewsletterQueue)
                .to(vellumExchange)
                .with(POST_PUBLISHED_ROUTING_KEY);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(
            ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }
}