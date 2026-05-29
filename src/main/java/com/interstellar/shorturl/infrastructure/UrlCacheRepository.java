package com.interstellar.shorturl.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

/**
 * Cache-Aside 패턴: 읽기 시 캐시 miss면 DB 조회 후 캐시에 저장.
 * 쓰기 시 DB 저장과 동시에 캐시에도 write-through.
 * TTL 24시간: 단축 URL 접근 패턴상 하루 이상 미접근 시 재조회 비용이 낮음.
 */
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
