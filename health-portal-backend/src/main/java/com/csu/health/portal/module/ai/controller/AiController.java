package com.csu.health.portal.module.ai.controller;

import com.csu.health.portal.common.Result;
import com.csu.health.portal.module.ai.dto.AiChatResponse;
import com.csu.health.portal.module.ai.dto.ChatRequest;
import com.csu.health.portal.module.ai.service.AiChatService;
import com.csu.health.portal.module.auth.entity.SysUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI健康问答")
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiChatService aiChatService;

    @Operation(summary = "健康知识问答")
    @PostMapping("/chat")
    public Result<AiChatResponse> chat(@Valid @RequestBody ChatRequest request,
                                       @AuthenticationPrincipal SysUser user) {
        Long userId = user == null ? null : user.getId();
        return Result.ok(aiChatService.chat(request, userId));
    }
}
