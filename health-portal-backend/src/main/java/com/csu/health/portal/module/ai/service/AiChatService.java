package com.csu.health.portal.module.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.csu.health.portal.common.BusinessException;
import com.csu.health.portal.module.ai.dto.AiChatResponse;
import com.csu.health.portal.module.ai.dto.AiHistoryMessage;
import com.csu.health.portal.module.ai.dto.AiSessionSummary;
import com.csu.health.portal.module.ai.dto.ChatRequest;
import com.csu.health.portal.module.ai.entity.AiChatHistory;
import com.csu.health.portal.module.ai.mapper.AiChatHistoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiChatService {

    private final ChatClient.Builder chatClientBuilder;
    private final AiChatHistoryMapper historyMapper;
    private final AiKnowledgeContextService knowledgeContextService;

    @Value("${spring.ai.openai.api-key:}")
    private String apiKey;

    private static final Pattern PORTAL_PROMO = Pattern.compile(
            ".*(开放数据|数据资源|资源目录|数据资源池|卫生政策栏目|健康百科|新闻中心|症状自查|"
                    + "可查阅|可以查阅|可以前往|建议前往|建议打开|请前往|请打开|门户的|/data|/policy|/knowledge|"
                    + "stats\\.gov\\.cn|data\\.stats\\.gov\\.cn|gov\\.cn/zhengce).*",
            Pattern.CASE_INSENSITIVE
    );

    private static final String SYSTEM_TEMPLATE = """
            你是「健康大数据应用创新研发中心门户」的健康知识助手。请用自然、简洁的中文直接回答用户问题。

            格式要求（必须遵守）：
            1. 禁止使用 Markdown：不要出现星号（*）、井号（#）、反引号、方括号链接写法，也不要用「- 」或「•」作项目符号。
            2. 可用自然段落；需要分点时只用「1.」「2.」「3.」这种纯数字序号，序号后直接写内容。
            3. 不要写「根据您的问题」「结合检索结果」「因此我为您提供」等套话，少做自我解释，直接给实用建议。

            内容规则：
            1. 下方【相关栏目检索】若有条目，可吸收其中事实与表述来作答；提到时用栏目名称点明即可。
            2. 若检索为空：不要说「未检索到」，不要解释检索过程，直接给通用、谨慎的健康常识建议。
            3. 不提供具体医疗诊断或处方；症状类问题只谈通用护理与何时就医。
            4. 绝对禁止在回答正文里引导用户去某个门户栏目、开放数据目录、政策库、症状自查、网站链接或「可查阅xxx」。功能跳转由系统界面单独用按钮展示，你只负责回答问题本身。
            5. 不要写「参考来源」「可查阅的健康数据资源」这类板块。

            【相关栏目检索】
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
            answer = "当前为演示模式，请配置 AI_API_KEY 后使用完整问答。\n您的问题：" + request.getMessage();
        } else {
            String context = knowledge.getContextText();
            if (context == null || context.isBlank()) {
                context = "（本题暂无匹配条目，请直接给出通用建议）";
            }
            String systemPrompt = SYSTEM_TEMPLATE.formatted(context);
            answer = chatClientBuilder.build()
                    .prompt()
                    .system(systemPrompt)
                    .user(request.getMessage())
                    .call()
                    .content();
            answer = normalizeAnswerStyle(answer);
        }

        saveHistory(userId, sessionId, "assistant", answer);

        AiChatResponse response = new AiChatResponse();
        response.setSessionId(sessionId);
        response.setAnswer(answer);
        response.setReferences(knowledge.getReferences());
        response.setActions(knowledge.getActions());
        return response;
    }

    public List<AiSessionSummary> listSessions(Long userId) {
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
        List<AiChatHistory> rows = historyMapper.selectList(new LambdaQueryWrapper<AiChatHistory>()
                .eq(AiChatHistory::getUserId, userId)
                .orderByDesc(AiChatHistory::getCreatedAt)
                .last("LIMIT 500"));

        Map<String, List<AiChatHistory>> bySession = new LinkedHashMap<>();
        for (AiChatHistory row : rows) {
            if (row.getSessionId() == null || row.getSessionId().isBlank()) {
                continue;
            }
            bySession.computeIfAbsent(row.getSessionId(), k -> new ArrayList<>()).add(row);
        }

        List<AiSessionSummary> list = new ArrayList<>();
        for (Map.Entry<String, List<AiChatHistory>> entry : bySession.entrySet()) {
            List<AiChatHistory> msgs = entry.getValue();
            msgs.sort(Comparator.comparing(AiChatHistory::getCreatedAt));
            AiSessionSummary summary = new AiSessionSummary();
            summary.setSessionId(entry.getKey());
            summary.setMessageCount(msgs.size());
            summary.setUpdatedAt(msgs.get(msgs.size() - 1).getCreatedAt());
            String title = msgs.stream()
                    .filter(m -> "user".equals(m.getRole()))
                    .map(AiChatHistory::getMessage)
                    .findFirst()
                    .orElse("未命名对话");
            summary.setTitle(truncate(title, 36));
            list.add(summary);
        }
        list.sort(Comparator.comparing(AiSessionSummary::getUpdatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return list.stream().limit(50).toList();
    }

    public List<AiHistoryMessage> getSessionMessages(Long userId, String sessionId) {
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
        if (sessionId == null || sessionId.isBlank()) {
            throw new BusinessException(400, "会话不存在");
        }
        List<AiChatHistory> rows = historyMapper.selectList(new LambdaQueryWrapper<AiChatHistory>()
                .eq(AiChatHistory::getUserId, userId)
                .eq(AiChatHistory::getSessionId, sessionId)
                .orderByAsc(AiChatHistory::getCreatedAt));
        if (rows.isEmpty()) {
            throw new BusinessException(404, "会话不存在或无权访问");
        }
        List<AiHistoryMessage> result = new ArrayList<>();
        for (AiChatHistory row : rows) {
            AiHistoryMessage msg = new AiHistoryMessage();
            msg.setRole(row.getRole());
            msg.setMessage(row.getMessage());
            msg.setCreatedAt(row.getCreatedAt());
            result.add(msg);
        }
        return result;
    }

    public void deleteSession(Long userId, String sessionId) {
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        historyMapper.delete(new LambdaQueryWrapper<AiChatHistory>()
                .eq(AiChatHistory::getUserId, userId)
                .eq(AiChatHistory::getSessionId, sessionId));
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

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        String oneLine = text.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= max ? oneLine : oneLine.substring(0, max) + "…";
    }

    static String normalizeAnswerStyle(String answer) {
        if (answer == null || answer.isBlank()) {
            return answer;
        }
        String text = answer
                .replace("\r\n", "\n")
                .replace("**", "")
                .replace("__", "")
                .replaceAll("(?m)^#{1,6}\\s*", "")
                .replaceAll("(?m)^\\s*[-*•]\\s+", "")
                .replaceAll("(?m)^\\s*(\\d+)[、.]\\s*", "$1. ");

        text = Arrays.stream(text.split("\n"))
                .filter(line -> !PORTAL_PROMO.matcher(line.trim()).matches())
                .collect(Collectors.joining("\n"));

        text = text.replaceAll("(?m)^参考来源[:：]?\\s*$", "")
                .replaceAll("(?m)^可查阅的健康数据资源[:：]?\\s*$", "")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
        return text;
    }
}
