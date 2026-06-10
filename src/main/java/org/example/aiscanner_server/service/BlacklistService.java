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
    private static final String TEMP_KEY_PREFIX = "blacklist:temp:";//临时黑名单的Key前缀，每个被封禁的用户都会有独立的Redis Key
    static final long TEMP_TTL_SECONDS = 86400; // 24 hours

    private final StringRedisTemplate redisTemplate;

    public BlacklistService(StringRedisTemplate redisTemplate) {  //实际上黑名单只需要对Redis做操作，持久化在后面
        this.redisTemplate = redisTemplate;
    }

    public record BlacklistHit(boolean hit, String source, String reason) {   //record是Java用法之一，自动生成持久化数据的方法，其生态和Lombok有些重叠。
        public static BlacklistHit miss() {
            return new BlacklistHit(false, null, null);
        }
    }

    /** Check all three blacklists. Authority checked first, then global, then temp. */
    public BlacklistHit checkBlacklist(String authorId) {
        try {
            if (Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(AUTHORITY_KEY, authorId))) {
                return new BlacklistHit(true, "authority", "权威黑名单发布者");
            }
            if (Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(GLOBAL_KEY, authorId))) {
                return new BlacklistHit(true, "global", "全局黑名单发布者");
            }
            String tempKey = TEMP_KEY_PREFIX + authorId;  //临时黑名单是键值对，这里将前缀和ID结合就成了键
            String tempValue = redisTemplate.opsForValue().get(tempKey); //通过传入的 key（键）去 Redis 中获取对应的 value（值）。
            if (tempValue != null) {
                return new BlacklistHit(true, "temp", "临时黑名单发布者: " + tempValue);
            }
        } catch (Exception e) {
            log.warn("Redis unavailable during blacklist check for authorId={}, allowing request", authorId, e);
        }
        return BlacklistHit.miss();
    }

    // ── Authority操作方法，使用Redis提供的模板方法对Redis操作，全局亦是如此。──────────────────────────────────────────

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
        redisTemplate.opsForValue().set(key, reason != null ? reason : "触发高危检测",  //reason为空时设置默认原因，但貌似我们没有在接口里提供设置Reason支持
                TEMP_TTL_SECONDS, TimeUnit.SECONDS);
        log.info("已加入临时黑名单, authorId={}, ttl=24h", authorId);
    }

    public void removeFromTemp(String authorId) {
        redisTemplate.delete(TEMP_KEY_PREFIX + authorId);
    }

    public Set<String> listTemp() { //展开所有临时黑名单
        Set<String> result = new HashSet<>();
        ScanOptions options = ScanOptions.scanOptions()
                .match(TEMP_KEY_PREFIX + "*") //匹配所有的黑名单键
                .count(100)     //一次只返回100个
                .build();
        /*这里是通过execute链接底层Redis结构，并执行原生命令。实际上当前版本的Spring Data Redis已经直接提供了Redis Scan的支持，但是这么写通用性更好。 */
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            try (Cursor<byte[]> cursor = connection.scan(options)) {
                while (cursor.hasNext()) { //cursor是一个迭代器，提供Scan等方法
                    String fullKey = new String(cursor.next(), StandardCharsets.UTF_8); //将字节数组按UTF-8解码成字符串
                    result.add(fullKey.substring(TEMP_KEY_PREFIX.length())); //去掉键的前缀，加到result中
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
