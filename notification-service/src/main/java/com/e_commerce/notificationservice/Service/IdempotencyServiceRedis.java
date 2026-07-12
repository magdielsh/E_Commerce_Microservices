package com.e_commerce.notificationservice.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyServiceRedis {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String KEY_PREFIX = "msg:processed:";
    private static final Duration TTL = Duration.ofHours(24); // ajusta según tu caso


    public boolean markAsProcessedIfAbsent(String messageId) {
        log.info("Insertando messageID en Redis: " + messageId);
        String key = KEY_PREFIX + messageId;
        Boolean inserted = redisTemplate.opsForValue()
                .setIfAbsent(key, "1", TTL);
        return Boolean.TRUE.equals(inserted);
    }

    public boolean isDuplicate(String messageId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + messageId));
    }


    public void markAsProcessed(String messageId) {
        redisTemplate.opsForValue()
                .set(KEY_PREFIX + messageId, "1", TTL);
    }

    public void remove(UUID messageId) {
        log.info("Eliminando messageID en Redis: " + messageId);
        redisTemplate.delete(KEY_PREFIX + messageId);
    }
}
