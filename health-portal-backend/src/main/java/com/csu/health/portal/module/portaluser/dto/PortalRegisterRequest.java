package com.csu.health.portal.module.portaluser.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PortalRegisterRequest {
    @NotBlank
    @Size(min = 3, max = 32)
    private String username;
    @NotBlank
    @Size(min = 6, max = 32)
    private String password;
    private String email;
    private String phone;
    private String realName;
}
