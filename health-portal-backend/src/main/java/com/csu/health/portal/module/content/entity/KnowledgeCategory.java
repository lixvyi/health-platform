package com.csu.health.portal.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("knowledge_category")
public class KnowledgeCategory {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("category_name")
    private String name;

    @TableField("category_code")
    private String code;

    @TableField(exist = false)
    private String icon;

    @TableField("display_order")
    private Integer sortOrder;

    private String description;
    private String externalUrl;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
