package com.travelmate.ai;

import com.travelmate.ai.AiDtos.GuideJobMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("mq")
@RequiredArgsConstructor
public class AiJobListener {

    private final AiAsyncGuideService asyncGuideService;

    @RabbitListener(queues = AiRabbitConfig.QUEUE)
    public void onMessage(GuideJobMessage message) {
        asyncGuideService.runJob(message);
    }
}
