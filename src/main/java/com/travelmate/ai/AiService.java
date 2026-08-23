package com.travelmate.ai;

import com.travelmate.ai.AiDtos.*;
import com.travelmate.common.ApiException;
import com.travelmate.domain.Spot;
import com.travelmate.repository.SpotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiService {

    private final QwenClient qwen;
    private final SpotRepository spotRepository;

    private static final String GUIDE_SYSTEM = String.join("\n",
            "你是可信的中文 AI 导游,正在为真实游客做边走边听讲解。",
            "只能使用用户消息里 availableFacts 与 routeContext 组织讲解,不得使用模型自带知识补充事实。",
            "任何未出现在 availableFacts 里的具体年代、尺寸、人物、事件、开放时间和距离,都禁止作为事实输出。",
            "区分可核实事实、观察建议与待核实信息;不要编造来源,不要输出 Markdown 标题或表情。",
            "输出自然口播文本,不超过 220 字。");

    private static final String CHAT_SYSTEM = String.join("\n",
            "你是正在带队的 AI 导游,回答要短、准确、可被打断。",
            "只能基于 availableFacts 回答;超出资料的知识点只回答“这个细节需要进一步核实”,再回到可确认的观察重点。",
            "不要编造来源。");

    private static final String VISION_SYSTEM = String.join("\n",
            "你是可信的 AI 旅行视觉导游,正在分析用户上传的真实图片或视频。",
            "只能使用 availableFacts 和画面可见内容,不得用模型自带知识补充景点事实。",
            "画面与资料不一致时,先说明“画面与当前景点资料未完全匹配”,再只描述画面本身。");

    public ExplanationResponse explanation(ExplanationRequest request) {
        Spot spot = requireSpot(request.spotId());
        List<Map<String, Object>> messages = List.of(
                Map.of("role", "system", "content", GUIDE_SYSTEM),
                Map.of("role", "user", "content", userPayload(spot, Map.of(
                        "task", "生成适合边走边听的导游讲解",
                        "style", styleName(request.style()),
                        "routeContext", nullToEmpty(request.routeContext())))));
        String content = qwen.chat(qwen.textModel(), messages);
        String factType = "verified".equals(spot.getSourceStatus()) ? "官方资料/可核实事实" : "待现场核实信息";
        return new ExplanationResponse(
                "guide-" + spot.getId() + "-" + System.currentTimeMillis(),
                spot.getName() + " · " + styleName(request.style()) + "讲解",
                content, spot.getSourceName(), factType, qwen.provider(), qwen.textModel());
    }

    public ChatResponse chat(ChatRequest request) {
        Spot spot = requireSpot(request.spotId());
        List<Map<String, Object>> messages = List.of(
                Map.of("role", "system", "content", CHAT_SYSTEM),
                Map.of("role", "user", "content", userPayload(spot, Map.of(
                        "question", nullToEmpty(request.question()),
                        "teamContext", nullToEmpty(request.teamContext())))));
        String answer = qwen.chat(qwen.textModel(), messages);
        return new ChatResponse(answer, spot.getSourceName(), qwen.provider(), qwen.textModel());
    }

    public VisionResponse vision(VisionRequest request) {
        Spot spot = requireSpot(request.spotId());
        if (request.mediaUrl() == null || request.mediaUrl().isBlank()) {
            throw ApiException.badRequest("视觉分析需要真实图片/关键帧/短视频地址,请传入 mediaUrl");
        }
        boolean video = isVideo(request.mediaUrl(), request.mode());
        Map<String, Object> mediaPart = video
                ? Map.of("type", "video_url", "video_url", Map.of("url", request.mediaUrl()))
                : Map.of("type", "image_url", "image_url", Map.of("url", request.mediaUrl()));
        Map<String, Object> textPart = Map.of("type", "text", "text", userPayload(spot, Map.of(
                "mode", nullToEmpty(request.mode()),
                "question", request.question() == null ? "请结合画面说明我正在看的内容。" : request.question())));
        List<Map<String, Object>> messages = List.of(
                Map.of("role", "system", "content", VISION_SYSTEM),
                Map.of("role", "user", "content", List.of(mediaPart, textPart)));
        String answer = qwen.chat(qwen.visionModel(), messages);
        return new VisionResponse(nullToEmpty(request.mode()), video ? "video" : "image",
                answer, qwen.provider(), qwen.visionModel());
    }

    Spot requireSpot(String spotId) {
        try {
            return spotRepository.findById(Long.valueOf(spotId))
                    .orElseThrow(() -> ApiException.notFound("景点不存在:" + spotId));
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("spotId 非法:" + spotId);
        }
    }

    private String userPayload(Spot spot, Map<String, Object> extra) {
        List<String> facts = factsForSpot(spot);
        StringBuilder sb = new StringBuilder();
        sb.append("availableFacts:\n");
        for (String f : facts) {
            sb.append("- ").append(f).append("\n");
        }
        sb.append("sourceName: ").append(spot.getSourceName()).append("\n");
        sb.append("sourceStatus: ").append(spot.getSourceStatus()).append("\n");
        extra.forEach((k, v) -> sb.append(k).append(": ").append(v).append("\n"));
        return sb.toString();
    }

    private List<String> factsForSpot(Spot s) {
        List<String> facts = new ArrayList<>();
        facts.add("地点:" + s.getName());
        facts.add("类别:" + s.getCategory());
        facts.add("简介:" + s.getIntro());
        facts.add("观察重点:" + s.getHighlight());
        facts.add("开放时间状态:" + s.getOpenTime());
        facts.add("建议停留:" + s.getRecommendedDuration());
        facts.add("标签:" + nullToEmpty(s.getTags()));
        return facts;
    }

    private boolean isVideo(String url, String mode) {
        String lower = url.split("\\?")[0].toLowerCase();
        return "video".equals(mode) || lower.startsWith("data:video/")
                || lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".avi");
    }

    private String styleName(String style) {
        return switch (style == null ? "story" : style) {
            case "brief" -> "简洁版";
            case "knowledge" -> "知识版";
            case "family" -> "亲子版";
            default -> "故事版";
        };
    }

    private String nullToEmpty(String v) {
        return v == null ? "" : v;
    }
}
