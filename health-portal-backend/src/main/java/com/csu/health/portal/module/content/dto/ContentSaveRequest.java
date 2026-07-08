package com.csu.health.portal.module.content.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalDate;

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
    private String sourceUrl;
    private String sourceName;
    private LocalDate sourcePublishDate;
    private String publisher;
    private LocalDateTime lastReviewTime;
    private String targetAudience;
    private String contentType;
    private Integer isMedical;
    private Integer hasEmergencyWarning;
    private String contraindications;
    private String adverseReactions;
    private String verificationStatus;
    private Integer status;
    private LocalDateTime publishTime;
}
