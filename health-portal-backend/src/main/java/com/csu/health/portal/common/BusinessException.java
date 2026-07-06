package com.csu.health.portal.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class BusinessException extends RuntimeException {
    private final int code;

    public BusinessException(String message) {
        super(message);
        this.code = HttpStatus.BAD_REQUEST.value();
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
