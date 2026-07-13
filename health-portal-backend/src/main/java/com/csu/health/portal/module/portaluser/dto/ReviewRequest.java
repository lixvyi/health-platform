package com.csu.health.portal.module.portaluser.dto;

import lombok.Data;

@Data
public class ReviewRequest {
    private boolean approved;
    private String remark;
}
