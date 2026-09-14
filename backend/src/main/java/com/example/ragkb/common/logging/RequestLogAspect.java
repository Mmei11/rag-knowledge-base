package com.example.ragkb.common.logging;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
public class RequestLogAspect {

    private static final Logger log = LoggerFactory.getLogger(RequestLogAspect.class);

    @Around("within(@org.springframework.web.bind.annotation.RestController *)")
    public Object logRequest(ProceedingJoinPoint joinPoint) throws Throwable {
        long startedAt = System.nanoTime();
        String request = requestSummary();
        String handler = joinPoint.getSignature().toShortString();

        try {
            Object result = joinPoint.proceed();
            log.info("request completed, request={}, handler={}, durationMs={}",
                    request,
                    handler,
                    elapsedMs(startedAt));
            return result;
        } catch (Throwable ex) {
            log.warn("request failed, request={}, handler={}, durationMs={}, error={}",
                    request,
                    handler,
                    elapsedMs(startedAt),
                    ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private String requestSummary() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            return request.getMethod() + " " + request.getRequestURI();
        }
        return "non-http";
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
