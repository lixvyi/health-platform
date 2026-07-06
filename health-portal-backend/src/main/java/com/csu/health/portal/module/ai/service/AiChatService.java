package com.csu.health.portal.module.ai.service;

import com.csu.health.portal.module.ai.dto.AiChatResponse;
import com.csu.health.portal.module.ai.dto.ChatRequest;
import com.csu.health.portal.module.ai.entity.AiChatHistory;
import com.csu.health.portal.module.ai.mapper.AiChatHistoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiChatService {

    private final ChatClient.Builder chatClientBuilder;
    private final AiChatHistoryMapper historyMapper;
    private final AiKnowledgeContextService knowledgeContextService;

    @Value("${spring.ai.openai.api-key:}")
    private String apiKey;

    private static final String SYSTEM_TEMPLATE = """
            你是「健康大数据应用创新研发中心门户」的专业健康知识助手。
            
            回答规则：
            1. 必须优先依据下方【门户知识库检索结果】作答；涉及政策、规划、纲要时引用具体标题。
            2. 若知识库中没有直接相关的政策或知识条目，应明确说明，并依据【推荐查阅链接】给出可访问的网址或门户页面路径，引导用户进一步查阅。
            3. 引用国家统计局或本门户开放数据时，须注明「来源：国家统计局」或对应平台名称。
            4. 语言简洁、专业、客观；不提供具体医疗诊断或处方，必要时提醒用户就医或咨询专业机构。
            5. 回答末尾用「参考来源：」列出 1～3 条最相关的标题或链接（从检索结果中选取）。
            
            【门户知识库检索结果】
            %s
            
            【推荐查阅链接】
            %s
            """;

    public AiChatResponse chat(ChatRequest request, Long userId) {
        String sessionId = request.getSessionId() == null || request.getSessionId().isBlank()
                ? UUID.randomUUID().toString() : request.getSessionId();

        saveHistory(userId, sessionId, "user", request.getMessage());

        AiKnowledgeContextService.KnowledgeBundle knowledge =
                knowledgeContextService.retrieve(request.getMessage());

        String answer;
        if (apiKey == null || apiKey.isBlank() || apiKey.startsWith("sk-placeholder")) {
            answer = "【演示模式】请配置 AI_API_KEY 或 application-secrets.yml 后使用真实 AI 问答。\n\n"
                    + "已检索到 " + knowledge.getReferences().size() + " 条相关知识源。\n"
                    + "您的问题：" + request.getMessage();
        } else {
            String systemPrompt = SYSTEM_TEMPLATE.formatted(
                    knowledge.getContextText(),
                    knowledge.getLinkText());
            answer = chatClientBuilder.build()
                    .prompt()
                    .system(systemPrompt)
                    .user(request.getMessage())
                    .call()
                    .content();
        }

        saveHistory(userId, sessionId, "assistant", answer);

        AiChatResponse response = new AiChatResponse();
        response.setSessionId(sessionId);
        response.setAnswer(answer);
        response.setReferences(knowledge.getReferences());
        return response;
    }

    private void saveHistory(Long userId, String sessionId, String role, String message) {
        AiChatHistory h = new AiChatHistory();
        h.setUserId(userId);
        h.setSessionId(sessionId);
        h.setRole(role);
        h.setMessage(message);
        h.setCreatedAt(LocalDateTime.now());
        historyMapper.insert(h);
    }
}
