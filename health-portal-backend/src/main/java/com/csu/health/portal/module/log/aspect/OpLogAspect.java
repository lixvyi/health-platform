package com.csu.health.portal.module.log.aspect;

import com.csu.health.portal.module.log.annotation.OpLog;
import com.csu.health.portal.module.log.entity.SysOperationLog;
import com.csu.health.portal.module.log.mapper.SysOperationLogMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class OpLogAspect {

    private final SysOperationLogMapper logMapper;
    private final ObjectMapper objectMapper;

    @Around("@annotation(com.csu.health.portal.module.log.annotation.OpLog)")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        long start = System.currentTimeMillis();
        SysOperationLog opLog = new SysOperationLog();
        opLog.setCreatedAt(LocalDateTime.now());

        try {
            // 执行方法
            Object result = point.proceed();
            opLog.setStatus(1);
            return result;
        } catch (Throwable e) {
            opLog.setStatus(0);
            opLog.setErrorMsg(e.getMessage() != null ? e.getMessage().substring(0, Math.min(500, e.getMessage().length())) : e.getClass().getName());
            throw e;
        } finally {
            try {
                // 获取注解信息
                MethodSignature signature = (MethodSignature) point.getSignature();
                Method method = signature.getMethod();
                OpLog annotation = method.getAnnotation(OpLog.class);
                opLog.setModule(annotation.module());
                opLog.setOperation(annotation.operation());
                opLog.setMethod(point.getTarget().getClass().getName() + "." + method.getName());

                // 获取参数
                Object[] args = point.getArgs();
                String params = objectMapper.writeValueAsString(args);
                opLog.setParams(params.length() > 1000 ? params.substring(0, 1000) + "..." : params);

                // 获取请求信息
                ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                if (attributes != null) {
                    HttpServletRequest request = attributes.getRequest();
                    opLog.setIp(getClientIp(request));
                }

                // 异步保存日志
                logMapper.insert(opLog);
                log.debug("操作日志: {} - {} 耗时: {}ms", opLog.getModule(), opLog.getOperation(), System.currentTimeMillis() - start);
            } catch (Exception e) {
                log.error("记录操作日志失败", e);
            }
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
