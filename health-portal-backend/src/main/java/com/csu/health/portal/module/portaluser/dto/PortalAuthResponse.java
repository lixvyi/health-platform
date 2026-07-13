package com.csu.health.portal.module.portaluser.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PortalAuthResponse {
    private String token;
    private Long userId;
    private String username;
    private String realName;
    private String role;
    private String certifyStatus;
    private String organization;
}
