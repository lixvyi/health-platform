package com.csu.health.portal.module.content.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ContentSaveRequest {
    @NotBlank
    private String categoryCode;
    @NotBlank
    private String title;
    private String summary;
    private String content;
    private String coverUrl;
    private String author;
    private Integer status;
    private LocalDateTime publishTime;
}
