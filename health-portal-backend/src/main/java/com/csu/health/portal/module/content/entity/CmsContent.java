package com.csu.health.portal.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalDate;

@Data
@TableName("cms_content")
public class CmsContent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String categoryCode;
    private String title;
    private String summary;
    private String content;
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
    private String coverUrl;
    private String author;
    private Integer viewCount;
    private Integer status;
    private LocalDateTime publishTime;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
