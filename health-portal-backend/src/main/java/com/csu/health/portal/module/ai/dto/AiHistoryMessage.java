package com.csu.health.portal.module.ai.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiHistoryMessage {
    private String role;
    private String message;
    private LocalDateTime createdAt;
}
