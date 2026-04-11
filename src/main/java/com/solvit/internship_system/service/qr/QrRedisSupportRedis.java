package com.solvit.internship_system.service.qr;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConditionalOnProperty(name = "app.qr.redis.enabled", havingValue = "true")
@RequiredArgsConstructor
public class QrRedisSupportRedis implements QrRedisSupport {

    private final StringRedisTemplate redisTemplate;

    @Override
    public long incrementWithExpire(String key, Duration ttlOnFirstIncrement) {
        Long c = redisTemplate.opsForValue().increment(key);
        if (c != null && c == 1) {
            redisTemplate.expire(key, ttlOnFirstIncrement);
        }
        return c != null ? c : 0L;
    }

    @Override
    public boolean hasKey(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    @Override
    public void set(String key, String value, Duration ttl) {
        redisTemplate.opsForValue().set(key, value, ttl);
    }

    @Override
    public long getCounter(String key) {
        String v = redisTemplate.opsForValue().get(key);
        if (v == null || v.isBlank()) {
            return 0;
        }
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
