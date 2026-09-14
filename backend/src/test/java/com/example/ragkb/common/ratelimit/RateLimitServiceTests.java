package com.example.ragkb.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ragkb.common.exception.BusinessException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RateLimitServiceTests {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Test
    void shouldSetTtlForFirstChatRequest() {
        ChatRateLimitProperties properties = properties();
        RateLimitService service = new RateLimitService(redisTemplate, properties);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("ragkb:rate-limit:chat:user:1")).thenReturn(1L);

        service.checkChatLimit(1L);

        verify(redisTemplate).expire("ragkb:rate-limit:chat:user:1", Duration.ofSeconds(60));
    }

    @Test
    void shouldRejectWhenChatLimitExceeded() {
        ChatRateLimitProperties properties = properties();
        properties.setLimit(2);
        RateLimitService service = new RateLimitService(redisTemplate, properties);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("ragkb:rate-limit:chat:user:1")).thenReturn(3L);

        assertThatThrownBy(() -> service.checkChatLimit(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("请求过于频繁，请稍后再试");
    }

    @Test
    void shouldSkipRedisWhenDisabled() {
        ChatRateLimitProperties properties = properties();
        properties.setEnabled(false);
        RateLimitService service = new RateLimitService(redisTemplate, properties);

        service.checkChatLimit(1L);

        verify(redisTemplate, never()).opsForValue();
    }

    private ChatRateLimitProperties properties() {
        ChatRateLimitProperties properties = new ChatRateLimitProperties();
        properties.setLimit(20);
        properties.setWindowSeconds(60);
        return properties;
    }
}
