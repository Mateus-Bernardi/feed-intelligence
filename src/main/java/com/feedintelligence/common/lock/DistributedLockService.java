package com.feedintelligence.common.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class DistributedLockService {

    private final StringRedisTemplate redisTemplate;

    // Tenta adquirir o lock — retorna true se conseguiu
    public boolean acquireLock(String key, Duration ttl) {
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(key, "locked", ttl);

        boolean result = Boolean.TRUE.equals(acquired);

        if (result) {
            log.debug("Lock acquired: {}", key);
        } else {
            log.debug("Lock already held: {}", key);
        }

        return result;
    }

    public void releaseLock(String key) {
        redisTemplate.delete(key);
        log.debug("Lock released: {}", key);
    }
}