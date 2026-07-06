package com.csu.health.portal.module.ai.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AiChatResponse {
    private String sessionId;
    private String answer;
    private List<AiKnowledgeRef> references = new ArrayList<>();
}
