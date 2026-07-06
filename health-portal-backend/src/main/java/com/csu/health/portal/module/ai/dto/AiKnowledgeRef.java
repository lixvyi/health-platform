package com.csu.health.portal.module.ai.dto;

import lombok.Data;

@Data
public class AiKnowledgeRef {
    private String type;
    private String title;
    private String url;
    private String excerpt;
    private String source;
}
