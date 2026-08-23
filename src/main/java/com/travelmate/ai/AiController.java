package com.travelmate.ai;

import com.travelmate.ai.AiDtos.*;
import com.travelmate.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;
    private final AiAsyncGuideService asyncGuideService;

    @PostMapping("/explanations")
    public Result<ExplanationResponse> explanation(@RequestBody ExplanationRequest request) {
        return Result.ok(aiService.explanation(request));
    }

    @PostMapping("/chat")
    public Result<ChatResponse> chat(@RequestBody ChatRequest request) {
        return Result.ok(aiService.chat(request));
    }

    @PostMapping("/vision")
    public Result<VisionResponse> vision(@RequestBody VisionRequest request) {
        return Result.ok(aiService.vision(request));
    }

    /** 提交异步讲解生成任务,返回 jobId 与 SSE 订阅地址。 */
    @PostMapping("/explanations/async")
    public Result<GuideJobResponse> submitAsync(@RequestBody ExplanationRequest request) {
        return Result.ok(asyncGuideService.submit(request));
    }

    /** 订阅任务进度(Server-Sent Events)。 */
    @GetMapping("/jobs/{jobId}/stream")
    public SseEmitter stream(@PathVariable String jobId) {
        return asyncGuideService.subscribe(jobId);
    }
}
