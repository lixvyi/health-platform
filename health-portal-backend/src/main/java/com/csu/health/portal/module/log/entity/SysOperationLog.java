package com.csu.health.portal.module.log.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_operation_log")
public class SysOperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String module;

    private String operation;

    private String method;

    private String params;

    private String ip;

    private Long userId;

    private String username;

    private Integer status;

    private String errorMsg;

    private LocalDateTime createdAt;
}
