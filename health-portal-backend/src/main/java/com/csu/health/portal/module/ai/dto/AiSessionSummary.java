package com.csu.health.portal.module.ai.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiSessionSummary {
    private String sessionId;
    private String title;
    private LocalDateTime updatedAt;
    private Integer messageCount;
}
