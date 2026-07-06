package com.csu.health.portal.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cms_app")
public class CmsApp {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String iconUrl;
    private String linkUrl;
    private String description;
    private Integer sortOrder;
    private Integer status;
    private LocalDateTime createdAt;
}
