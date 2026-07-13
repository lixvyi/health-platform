package com.csu.health.portal.module.portaluser.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ApplyRequest {
    @NotBlank
    private String projectName;
    @NotBlank
    private String purpose;
    private String reason;
}
