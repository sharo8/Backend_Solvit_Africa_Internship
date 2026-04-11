package com.solvit.internship_system.service.qr;

import java.time.Duration;

/**
 * Rate limit, nonce blacklist, and scan cooldown for dynamic QR.
 * Backed by Redis when {@code app.qr.redis.enabled=true}, otherwise in-memory (dev-friendly).
 */
public interface QrRedisSupport {

    long incrementWithExpire(String key, Duration ttlOnFirstIncrement);

    boolean hasKey(String key);

    void set(String key, String value, Duration ttl);

    /** Current counter value for daily keys (0 if missing or expired). */
    long getCounter(String key);
}
