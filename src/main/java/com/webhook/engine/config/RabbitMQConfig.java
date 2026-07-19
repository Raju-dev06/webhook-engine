package com.webhook.engine.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMQConfig {

    public static final String MAIN_EXCHANGE = "webhook.exchange";
    public static final String MAIN_QUEUE = "webhook.main.queue";
    public static final String ROUTING_KEY = "webhook.routing.key";

    public static final String RETRY_EXCHANGE = "webhook.retry.exchange";
    
    public static final String DLX_EXCHANGE = "webhook.dlx";
    public static final String DLQ_QUEUE = "webhook.dlq";

    // Exchanges
    @Bean
    public TopicExchange mainExchange() {
        return new TopicExchange(MAIN_EXCHANGE);
    }

    @Bean
    public DirectExchange retryExchange() {
        return new DirectExchange(RETRY_EXCHANGE);
    }

    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(DLX_EXCHANGE);
    }

    // Queues
    @Bean
    public Queue mainQueue() {
        return QueueBuilder.durable(MAIN_QUEUE)
                .deadLetterExchange(DLX_EXCHANGE)
                .deadLetterRoutingKey(DLQ_QUEUE)
                .build();
    }

    @Bean
    public Queue dlqQueue() {
        return QueueBuilder.durable(DLQ_QUEUE).build();
    }

    // Bindings
    @Bean
    public Binding mainBinding(Queue mainQueue, TopicExchange mainExchange) {
        return BindingBuilder.bind(mainQueue).to(mainExchange).with(ROUTING_KEY);
    }

    @Bean
    public Binding dlqBinding(Queue dlqQueue, DirectExchange dlxExchange) {
        return BindingBuilder.bind(dlqQueue).to(dlxExchange).with(DLQ_QUEUE);
    }

    // Retry Queues (5s, 10s, 20s, 40s)
    @Bean
    public Queue retryQueue5s() {
        return createRetryQueue("webhook.retry.5s", 5000);
    }

    @Bean
    public Queue retryQueue10s() {
        return createRetryQueue("webhook.retry.10s", 10000);
    }

    @Bean
    public Queue retryQueue20s() {
        return createRetryQueue("webhook.retry.20s", 20000);
    }

    @Bean
    public Queue retryQueue40s() {
        return createRetryQueue("webhook.retry.40s", 40000);
    }

    // Retry Bindings
    @Bean
    public Binding retryBinding5s() {
        return BindingBuilder.bind(retryQueue5s()).to(retryExchange()).with("retry.5s");
    }

    @Bean
    public Binding retryBinding10s() {
        return BindingBuilder.bind(retryQueue10s()).to(retryExchange()).with("retry.10s");
    }

    @Bean
    public Binding retryBinding20s() {
        return BindingBuilder.bind(retryQueue20s()).to(retryExchange()).with("retry.20s");
    }

    @Bean
    public Binding retryBinding40s() {
        return BindingBuilder.bind(retryQueue40s()).to(retryExchange()).with("retry.40s");
    }

    private Queue createRetryQueue(String name, int ttlMs) {
        Map<String, Object> args = new HashMap<>();
        args.put("x-message-ttl", ttlMs);
        args.put("x-dead-letter-exchange", MAIN_EXCHANGE);
        args.put("x-dead-letter-routing-key", ROUTING_KEY);
        return new Queue(name, true, false, false, args);
    }
}
