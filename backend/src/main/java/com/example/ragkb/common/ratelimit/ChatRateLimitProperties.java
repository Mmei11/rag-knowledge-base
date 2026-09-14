package com.example.ragkb.common.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.rate-limit.chat")
public class ChatRateLimitProperties {

    private boolean enabled = true;

    private int limit = 20;

    private long windowSeconds = 60;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("chat rate limit must be positive");
        }
        this.limit = limit;
    }

    public long getWindowSeconds() {
        return windowSeconds;
    }

    public void setWindowSeconds(long windowSeconds) {
        if (windowSeconds <= 0) {
            throw new IllegalArgumentException("chat rate limit window must be positive");
        }
        this.windowSeconds = windowSeconds;
    }
}
