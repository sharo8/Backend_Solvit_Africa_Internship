package com.solvit.internship_system.service.qr;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-process TTL map for QR state when Redis is not used.
 */
@Component
@ConditionalOnProperty(name = "app.qr.redis.enabled", havingValue = "false", matchIfMissing = true)
public class QrRedisSupportMemory implements QrRedisSupport {

    private static final class Counter {
        long count;
        long expireAtMillis;

        Counter(long count, long expireAtMillis) {
            this.count = count;
            this.expireAtMillis = expireAtMillis;
        }
    }

    private static final class Value {
        String val;
        long expireAtMillis;

        Value(String val, long expireAtMillis) {
            this.val = val;
            this.expireAtMillis = expireAtMillis;
        }
    }

    private final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Value> strings = new ConcurrentHashMap<>();

    @Override
    public long incrementWithExpire(String key, Duration ttlOnFirstIncrement) {
        long now = System.currentTimeMillis();
        long ttlMs = ttlOnFirstIncrement.toMillis();
        return counters.compute(key, (k, existing) -> {
            if (existing == null || now > existing.expireAtMillis) {
                return new Counter(1, now + ttlMs);
            }
            existing.count++;
            return existing;
        }).count;
    }

    @Override
    public boolean hasKey(String key) {
        long now = System.currentTimeMillis();
        Value v = strings.get(key);
        if (v != null) {
            if (now > v.expireAtMillis) {
                strings.remove(key, v);
                return false;
            }
            return true;
        }
        Counter c = counters.get(key);
        if (c == null) {
            return false;
        }
        if (now > c.expireAtMillis) {
            counters.remove(key, c);
            return false;
        }
        return true;
    }

    @Override
    public void set(String key, String value, Duration ttl) {
        long now = System.currentTimeMillis();
        strings.put(key, new Value(value, now + ttl.toMillis()));
    }

    @Override
    public long getCounter(String key) {
        long now = System.currentTimeMillis();
        Value v = strings.get(key);
        if (v != null) {
            if (now > v.expireAtMillis) {
                strings.remove(key, v);
            } else {
                try {
                    return Long.parseLong(v.val.trim());
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
        }
        Counter c = counters.get(key);
        if (c == null) {
            return 0;
        }
        if (now > c.expireAtMillis) {
            counters.remove(key, c);
            return 0;
        }
        return c.count;
    }
}
