package com.csu.health.portal.module.portaluser.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PortalLoginRequest {
    @NotBlank
    private String username;
    @NotBlank
    private String password;
}
