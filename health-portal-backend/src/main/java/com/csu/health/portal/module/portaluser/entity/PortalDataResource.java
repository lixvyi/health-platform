package com.csu.health.portal.module.portaluser.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("portal_data_resource")
public class PortalDataResource {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private String category;
    private String source;
    private String description;
    private String dataType;
    private String sizeLabel;
    private String permissionLevel;
    private String openDataId;
    private Long refContentId;
    private Integer status;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
