package com.csu.health.portal.module.ai.controller;

import com.csu.health.portal.common.Result;
import com.csu.health.portal.module.ai.dto.AiChatResponse;
import com.csu.health.portal.module.ai.dto.AiHistoryMessage;
import com.csu.health.portal.module.ai.dto.AiSessionSummary;
import com.csu.health.portal.module.ai.dto.ChatRequest;
import com.csu.health.portal.module.ai.service.AiChatService;
import com.csu.health.portal.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "AI健康问答")
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiChatService aiChatService;

    @Operation(summary = "健康知识问答")
    @PostMapping("/chat")
    public Result<AiChatResponse> chat(@Valid @RequestBody ChatRequest request,
                                       @AuthenticationPrincipal AuthPrincipal principal) {
        Long userId = resolvePortalUserId(principal);
        return Result.ok(aiChatService.chat(request, userId));
    }

    @Operation(summary = "当前用户的对话会话列表")
    @GetMapping("/sessions")
    public Result<List<AiSessionSummary>> sessions(@AuthenticationPrincipal AuthPrincipal principal) {
        Long userId = requirePortalUserId(principal);
        return Result.ok(aiChatService.listSessions(userId));
    }

    @Operation(summary = "加载指定会话消息")
    @GetMapping("/sessions/{sessionId}")
    public Result<List<AiHistoryMessage>> sessionMessages(@PathVariable String sessionId,
                                                          @AuthenticationPrincipal AuthPrincipal principal) {
        Long userId = requirePortalUserId(principal);
        return Result.ok(aiChatService.getSessionMessages(userId, sessionId));
    }

    @Operation(summary = "删除指定会话")
    @DeleteMapping("/sessions/{sessionId}")
    public Result<Void> deleteSession(@PathVariable String sessionId,
                                      @AuthenticationPrincipal AuthPrincipal principal) {
        Long userId = requirePortalUserId(principal);
        aiChatService.deleteSession(userId, sessionId);
        return Result.ok();
    }

    private static Long resolvePortalUserId(AuthPrincipal principal) {
        if (principal == null || !principal.isPortal()) {
            return null;
        }
        return principal.getUserId();
    }

    private static Long requirePortalUserId(AuthPrincipal principal) {
        Long userId = resolvePortalUserId(principal);
        if (userId == null) {
            throw new com.csu.health.portal.common.BusinessException(401, "请先登录门户账号");
        }
        return userId;
    }
}
