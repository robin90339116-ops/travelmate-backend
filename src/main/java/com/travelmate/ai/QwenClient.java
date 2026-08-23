package com.travelmate.ai;

import com.travelmate.common.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * 通义千问(阿里百炼,OpenAI 兼容接口)客户端。支持文本与视觉多模态。
 */
@Slf4j
@Component
public class QwenClient {

    private final String apiKey;
    private final String baseUrl;
    private final String textModel;
    private final String visionModel;
    private final String provider;
    private final RestClient restClient = RestClient.create();

    public QwenClient(
            @Value("${app.ai.api-key:}") String apiKey,
            @Value("${app.ai.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}") String baseUrl,
            @Value("${app.ai.text-model:qwen-plus}") String textModel,
            @Value("${app.ai.vision-model:qwen-vl-plus}") String visionModel,
            @Value("${app.ai.provider:bailian}") String provider) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.textModel = textModel;
        this.visionModel = visionModel;
        this.provider = provider;
    }

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String provider() {
        return provider;
    }

    public String textModel() {
        return textModel;
    }

    public String visionModel() {
        return visionModel;
    }

    @SuppressWarnings("unchecked")
    public String chat(String model, List<Map<String, Object>> messages) {
        if (!configured()) {
            throw ApiException.serviceUnavailable("AI Provider 未配置,请设置 app.ai.api-key(DASHSCOPE_API_KEY)");
        }
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", messages,
                "temperature", 0.4);
        try {
            Map<String, Object> response = restClient.post()
                    .uri(baseUrl.replaceAll("/+$", "") + "/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            if (response == null) {
                throw ApiException.serviceUnavailable("AI Provider 返回为空");
            }
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            if (choices == null || choices.isEmpty()) {
                throw ApiException.serviceUnavailable("AI Provider 无有效结果");
            }
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            Object content = message == null ? null : message.get("content");
            if (content == null || content.toString().isBlank()) {
                throw ApiException.serviceUnavailable("AI Provider 返回内容为空");
            }
            return content.toString();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用 AI Provider 失败", e);
            throw ApiException.serviceUnavailable("调用 AI Provider 失败:" + e.getMessage());
        }
    }
}
