package org.example.aiscanner_server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
/**限流*/
@Service
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

    private static final String RATE_LIMIT_KEY_PREFIX = "ratelimit:";

    private final StringRedisTemplate redisTemplate;

    public RateLimitService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public record RateLimitResult(boolean allowed, long remaining, long resetTimeSeconds, int limit) {}

    /**
     * Check rate limit for a given key identifier using fixed-window counting.
     *
     * @param keyIdentifier  unique identifier for the key (key value)
     * @param limit          max requests per window
     * @param windowSeconds  window duration in seconds
     * @return RateLimitResult with status and metadata
     */
    public RateLimitResult checkRate(String keyIdentifier, int limit, int windowSeconds) { //分别是标识，最大次数，窗口市场
        long now = System.currentTimeMillis() / 1000; //获取当前秒级时间戳
        long windowStart = now / windowSeconds * windowSeconds;  //当前窗口起始时间
        String redisKey = RATE_LIMIT_KEY_PREFIX + keyIdentifier + ":" + windowStart;  //确保每个窗口的key不一样

        try {
            Long count = redisTemplate.opsForValue().increment(redisKey);  //increment是原子操作，返回已请求次数
            if (count != null && count == 1) {
                redisTemplate.expire(redisKey, windowSeconds, TimeUnit.SECONDS);/**该窗口的第一次请求，设置 key 的过期时间为 windowSeconds 秒。*/
                                                                                 /**这样窗口结束后 Redis 会自动清理 key，节省内存。*/
            }
            /**count 和 currentCount 在数值上是同一个值*/
            long currentCount = count != null ? count : 0;//currentCount是已收到的请求次数
            boolean allowed = currentCount <= limit;  //若收到请求数大于限制数
            long remaining = Math.max(0, limit - currentCount);  //计算剩余次数
            long resetTime = windowStart + windowSeconds;   //计算重置时间

            if (!allowed) {
                log.warn("Rate limit exceeded for key={}, count={}, limit={}", maskKey(keyIdentifier), currentCount, limit);
            }

            return new RateLimitResult(allowed, remaining, resetTime, limit);
        } catch (Exception e) {
            log.warn("Redis unavailable during rate check, allowing request", e);
            return new RateLimitResult(true, limit, now + windowSeconds, limit);
        }
    }

    private String maskKey(String key) {
        if (key == null || key.length() <= 8) return "***";
        return key.substring(0, 4) + "..." + key.substring(key.length() - 4);
    }
}
