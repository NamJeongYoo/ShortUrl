package com.interstellar.shorturl.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UrlCacheRepository {

    private static final String KEY_PREFIX = "shorturl:";
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;

    public Optional<String> get(String shortCode) {
        String value = redisTemplate.opsForValue().get(KEY_PREFIX + shortCode);
        return Optional.ofNullable(value);
    }

    public void save(String shortCode, String originalUrl) {
        redisTemplate.opsForValue().set(KEY_PREFIX + shortCode, originalUrl, TTL);
    }
}
