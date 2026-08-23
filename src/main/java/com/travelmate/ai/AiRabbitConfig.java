package com.travelmate.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * AI 讲解异步管线的 RabbitMQ 拓扑(仅 mq profile)。
 * 主队列绑定死信交换机,失败消息进入死信队列,支持排查与重试。
 */
@Configuration
@Profile("mq")
public class AiRabbitConfig {

    public static final String EXCHANGE = "guide.exchange";
    public static final String QUEUE = "guide.job.queue";
    public static final String ROUTING_KEY = "guide.job";
    public static final String DLX = "guide.dlx";
    public static final String DLQ = "guide.job.dlq";

    @Bean
    public DirectExchange guideExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange guideDlx() {
        return new DirectExchange(DLX, true, false);
    }

    @Bean
    public Queue guideQueue() {
        return QueueBuilder.durable(QUEUE)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(DLQ)
                .build();
    }

    @Bean
    public Queue guideDlq() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    public Binding guideBinding() {
        return BindingBuilder.bind(guideQueue()).to(guideExchange()).with(ROUTING_KEY);
    }

    @Bean
    public Binding guideDlqBinding() {
        return BindingBuilder.bind(guideDlq()).to(guideDlx()).with(DLQ);
    }

    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
