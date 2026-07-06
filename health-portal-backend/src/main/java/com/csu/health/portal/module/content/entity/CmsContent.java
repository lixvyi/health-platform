package com.csu.health.portal.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

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
    private String coverUrl;
    private String author;
    private Integer viewCount;
    private Integer status;
    private LocalDateTime publishTime;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
