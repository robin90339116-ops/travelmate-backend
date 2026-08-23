package com.travelmate.ai;

import com.travelmate.ai.AiDtos.GuideJobMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("mq")
@RequiredArgsConstructor
public class AiJobProducer {

    private final RabbitTemplate rabbitTemplate;

    public void send(GuideJobMessage message) {
        rabbitTemplate.convertAndSend(AiRabbitConfig.EXCHANGE, AiRabbitConfig.ROUTING_KEY, message);
    }
}
