package org.example.aiscanner_server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class BlacklistService {

    private static final Logger log = LoggerFactory.getLogger(BlacklistService.class);

    private static final String AUTHORITY_KEY = "blacklist:authority";
    private static final String GLOBAL_KEY = "blacklist:global";
    private static final String TEMP_KEY_PREFIX = "blacklist:temp:";
    static final long TEMP_TTL_SECONDS = 86400; // 24 hours

    private final StringRedisTemplate redisTemplate;

    public BlacklistService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public record BlacklistHit(boolean hit, String source, String reason) {
        public static BlacklistHit miss() {
            return new BlacklistHit(false, null, null);
        }
    }

    /** Check all three blacklists. Authority checked first, then global, then temp. */
    public BlacklistHit checkBlacklist(String authorId) {
        if (Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(AUTHORITY_KEY, authorId))) {
            return new BlacklistHit(true, "authority", "权威黑名单发布者");
        }
        if (Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(GLOBAL_KEY, authorId))) {
            return new BlacklistHit(true, "global", "全局黑名单发布者");
        }
        String tempKey = TEMP_KEY_PREFIX + authorId;
        String tempValue = redisTemplate.opsForValue().get(tempKey);
        if (tempValue != null) {
            return new BlacklistHit(true, "temp", "临时黑名单发布者: " + tempValue);
        }
        return BlacklistHit.miss();
    }

    // ── Authority操作方法 ──────────────────────────────────────────

    public boolean isInAuthority(String authorId) {
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(AUTHORITY_KEY, authorId));
    }

    public void addToAuthority(String authorId) {
        redisTemplate.opsForSet().add(AUTHORITY_KEY, authorId);
    }

    public void removeFromAuthority(String authorId) {
        redisTemplate.opsForSet().remove(AUTHORITY_KEY, authorId);
    }

    public Set<String> listAuthority() {
        Set<String> members = redisTemplate.opsForSet().members(AUTHORITY_KEY);
        return members != null ? members : Set.of();
    }

    // ── Global ────────────────────────────────────────────

    public boolean isInGlobal(String authorId) {
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(GLOBAL_KEY, authorId));
    }

    public void addToGlobal(String authorId) {
        redisTemplate.opsForSet().add(GLOBAL_KEY, authorId);
    }

    public void removeFromGlobal(String authorId) {
        redisTemplate.opsForSet().remove(GLOBAL_KEY, authorId);
    }

    public Set<String> listGlobal() {
        Set<String> members = redisTemplate.opsForSet().members(GLOBAL_KEY);
        return members != null ? members : Set.of();
    }

    // ── Temp (24h TTL) ────────────────────────────────────

    public boolean isInTemp(String authorId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(TEMP_KEY_PREFIX + authorId));
    }

    public void addToTemp(String authorId, String reason) {
        String key = TEMP_KEY_PREFIX + authorId;
        redisTemplate.opsForValue().set(key, reason != null ? reason : "触发高危检测",
                TEMP_TTL_SECONDS, TimeUnit.SECONDS);
        log.info("已加入临时黑名单, authorId={}, ttl=24h", authorId);
    }

    public void removeFromTemp(String authorId) {
        redisTemplate.delete(TEMP_KEY_PREFIX + authorId);
    }

    public Set<String> listTemp() {
        Set<String> result = new HashSet<>();
        ScanOptions options = ScanOptions.scanOptions()
                .match(TEMP_KEY_PREFIX + "*")
                .count(100)
                .build();
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            try (Cursor<byte[]> cursor = connection.scan(options)) {
                while (cursor.hasNext()) {
                    String fullKey = new String(cursor.next(), StandardCharsets.UTF_8);
                    result.add(fullKey.substring(TEMP_KEY_PREFIX.length()));
                    if (result.size() >= 1000) break;
                }
            } catch (Exception e) {
                log.warn("扫描临时黑名单失败", e);
            }
            return null;
        });
        return result;
    }

    public Long getTempTtl(String authorId) {
        return redisTemplate.getExpire(TEMP_KEY_PREFIX + authorId, TimeUnit.SECONDS);
    }
}
