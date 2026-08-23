package com.travelmate.ai;

import com.travelmate.ai.AiDtos.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * 异步 AI 讲解生成:
 * - dev 默认:线程池(guideExecutor)执行,SSE 推送进度。
 * - prod(mq profile,app.async.mode=rabbit):投递 RabbitMQ,由监听器消费后执行,实现削峰与失败重试。
 */
@Slf4j
@Service
public class AiAsyncGuideService {

    private final AiService aiService;
    private final Executor guideExecutor;
    private final ObjectProvider<AiJobProducer> producerProvider;
    private final String asyncMode;

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final Map<String, String> completed = new ConcurrentHashMap<>();

    public AiAsyncGuideService(AiService aiService,
                               @Qualifier("guideExecutor") Executor guideExecutor,
                               ObjectProvider<AiJobProducer> producerProvider,
                               @Value("${app.async.mode:local}") String asyncMode) {
        this.aiService = aiService;
        this.guideExecutor = guideExecutor;
        this.producerProvider = producerProvider;
        this.asyncMode = asyncMode;
    }

    public GuideJobResponse submit(ExplanationRequest request) {
        String jobId = UUID.randomUUID().toString();
        GuideJobMessage message = new GuideJobMessage(jobId, request.spotId(), request.style(), request.routeContext());
        AiJobProducer producer = producerProvider.getIfAvailable();
        if ("rabbit".equalsIgnoreCase(asyncMode) && producer != null) {
            producer.send(message);
        } else {
            guideExecutor.execute(() -> runJob(message));
        }
        return new GuideJobResponse(jobId, "queued", "/api/ai/jobs/" + jobId + "/stream");
    }

    public SseEmitter subscribe(String jobId) {
        SseEmitter emitter = new SseEmitter(120_000L);
        emitter.onCompletion(() -> emitters.remove(jobId));
        emitter.onTimeout(() -> emitters.remove(jobId));
        String done = completed.get(jobId);
        if (done != null) {
            sendResult(emitter, jobId, done);
        } else {
            emitters.put(jobId, emitter);
        }
        return emitter;
    }

    /** 实际任务:推送进度 -> 调用 AI -> 推送结果。 */
    public void runJob(GuideJobMessage message) {
        String jobId = message.jobId();
        try {
            emit(jobId, "progress", Map.of("percent", 10, "stage", "已入队"));
            emit(jobId, "progress", Map.of("percent", 40, "stage", "正在生成讲解"));
            ExplanationResponse response = aiService.explanation(
                    new ExplanationRequest(message.spotId(), message.style(), message.routeContext()));
            emit(jobId, "progress", Map.of("percent", 90, "stage", "整理结果"));
            String content = response.content();
            completed.put(jobId, content);
            SseEmitter emitter = emitters.get(jobId);
            if (emitter != null) {
                sendResult(emitter, jobId, content);
            }
        } catch (Exception e) {
            log.error("异步讲解任务失败 jobId={}", jobId, e);
            SseEmitter emitter = emitters.get(jobId);
            if (emitter != null) {
                try {
                    emitter.send(SseEmitter.event().name("error").data(Map.of("message", e.getMessage())));
                    emitter.complete();
                } catch (Exception ignored) {
                    emitter.completeWithError(e);
                }
            }
        }
    }

    private void sendResult(SseEmitter emitter, String jobId, String content) {
        try {
            emitter.send(SseEmitter.event().name("progress").data(Map.of("percent", 100, "stage", "完成")));
            emitter.send(SseEmitter.event().name("result").data(Map.of("jobId", jobId, "content", content)));
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    }

    private void emit(String jobId, String name, Object data) {
        SseEmitter emitter = emitters.get(jobId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().name(name).data(data));
            } catch (Exception ignored) {
                emitters.remove(jobId);
            }
        }
    }
}
