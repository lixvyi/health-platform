package com.csu.health.portal.module.portaluser.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CertifyRequest {
    @NotBlank
    private String realName;
    @NotBlank
    private String organization;
    @NotBlank
    private String orgType;
    private String researchDirection;
    @NotBlank
    private String certifyReason;
}
