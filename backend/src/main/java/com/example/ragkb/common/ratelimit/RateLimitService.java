package com.example.ragkb.common.ratelimit;

import com.example.ragkb.common.exception.BusinessException;
import com.example.ragkb.common.exception.ErrorCode;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RateLimitService {

    private static final String CHAT_KEY_PREFIX = "ragkb:rate-limit:chat:user:";

    private final StringRedisTemplate redisTemplate;
    private final ChatRateLimitProperties properties;

    public RateLimitService(
            StringRedisTemplate redisTemplate,
            ChatRateLimitProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    public void checkChatLimit(Long userId) {
        if (!properties.isEnabled()) {
            return;
        }

        String key = CHAT_KEY_PREFIX + userId;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, Duration.ofSeconds(properties.getWindowSeconds()));
        }

        if (count != null && count > properties.getLimit()) {
            throw new BusinessException(ErrorCode.RATE_LIMITED);
        }
    }
}
