package org.example.aiscanner_server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.aiscanner_server.mapper.ApiKeyMapper;
import org.example.aiscanner_server.model.entity.ApiKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class ApiKeyService {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyService.class);//日志方法工厂

    private static final String CACHE_KEY_PREFIX = "apikey:";
    private static final long CACHE_TTL_SECONDS = 300; // 5 minutes

    private final ApiKeyMapper apiKeyMapper;  //数据持久化
    private final StringRedisTemplate redisTemplate;  //Redis操作
    private final ObjectMapper objectMapper; //序列化支持

    public ApiKeyService(ApiKeyMapper apiKeyMapper, StringRedisTemplate redisTemplate,
                         ObjectMapper objectMapper) {
        this.apiKeyMapper = apiKeyMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    // ── 登记设备──────────────────────────────────────────

    /**
     * 登记一个新的设备Key，如果已经存在就返回现有的。
     */
    public ApiKey registerKey(String deviceId, String deviceName) {
        ApiKey existing = apiKeyMapper.selectByDeviceId(deviceId);  //在数据库中查找Key。
        if (existing != null) {
            log.info("Device {} already has a key, returning existing key id={}", deviceId, existing.getId());
            return existing;
        }

        ApiKey apiKey = new ApiKey();  //生成Key
        apiKey.setKeyValue(generateKey());
        apiKey.setKeyName(deviceName != null ? deviceName : deviceId);
        apiKey.setDeviceId(deviceId);
        apiKey.setPermissions("DETECT,HISTORY");
        apiKey.setStatus("ACTIVE");
        apiKey.setRateLimit(20);

        apiKeyMapper.insert(apiKey);
        cacheKey(apiKey);
        log.info("Registered new API key for device={}, id={}", deviceId, apiKey.getId());
        return apiKey;
    }

    // ── Key认证 ─────────────────────────────

    /**
     * 认证Key，若有效且未过期，返回Key实体
     * Checks Redis cache first, falls back to MySQL.
     */
    public Optional<ApiKey> validateKey(String keyValue) {
        // Try Redis cache first
        String cacheKey = CACHE_KEY_PREFIX + keyValue;
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey); //获取缓存的Json，其中的status就是需要用到的。
            if (cached != null) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = objectMapper.readValue(cached, Map.class); //将Json反序列化为Map
                    ApiKey apiKey = mapToApiKey(map); //将其转化为Apikey实体
                    return isKeyUsable(apiKey) ? Optional.of(apiKey) : Optional.empty();  //检查是否可用，实则检查status。
                } catch (JsonProcessingException e) {
                    log.warn("Failed to deserialize cached API key, will query MySQL", e);
                    redisTemplate.delete(cacheKey);
                }
            }
        } catch (Exception e) {
            log.warn("Redis unavailable, falling back to MySQL for key validation", e);
            //Redis查询失败，转跳到数据库
        }

        // 进入数据库查找
        ApiKey apiKey = apiKeyMapper.selectByKeyValue(keyValue);
        if (apiKey == null) {
            return Optional.empty();
        }
        try {
            cacheKey(apiKey); //将查询到的插入进Redis中
        } catch (Exception e) {
            log.debug("Failed to cache API key to Redis", e);
        }
        return isKeyUsable(apiKey) ? Optional.of(apiKey) : Optional.empty(); //认证
    }

    // ── 管理员：管理Key ────────────────────────────────

    public ApiKey createKey(String keyName, String permissions, Integer rateLimit, LocalDateTime expiredAt) {
        ApiKey apiKey = new ApiKey();
        apiKey.setKeyValue(generateKey());
        apiKey.setKeyName(keyName);
        apiKey.setPermissions(permissions != null ? permissions : "DETECT,HISTORY");
        apiKey.setStatus("ACTIVE");
        apiKey.setRateLimit(rateLimit != null ? rateLimit : 20);
        apiKey.setExpiredAt(expiredAt);  //传参

        apiKeyMapper.insert(apiKey); //在数据库中插入
        cacheKey(apiKey); //在Redis中缓存
        log.info("Admin created API key id={}, name={}", apiKey.getId(), keyName);
        return apiKey;
    }

    public Optional<ApiKey> getById(Long id) {
        ApiKey key = apiKeyMapper.selectById(id);
        return Optional.ofNullable(key); //optional容器类，允许将空值Null包装为容器
    }

    public List<ApiKey> listAll() {
        return apiKeyMapper.selectAll();
    } //列出所有Key

    public void updateKey(Long id, String keyName, String permissions, Integer rateLimit, LocalDateTime expiredAt) {
        ApiKey key = apiKeyMapper.selectById(id);
        if (key == null) throw new IllegalArgumentException("Key not found: " + id);

        if (keyName != null) key.setKeyName(keyName);
        if (permissions != null) key.setPermissions(permissions);
        if (rateLimit != null) key.setRateLimit(rateLimit);
        key.setExpiredAt(expiredAt); // nullable, can clear

        apiKeyMapper.update(key);
        invalidateCache(key.getKeyValue());
        log.info("Updated API key id={}", id);
    }

    public void revokeKey(Long id) {  //撤销Key，撤销是可逆的，适用于紧急收回某个Key，并保留它以便事后追责。
        ApiKey key = apiKeyMapper.selectById(id);
        if (key == null) throw new IllegalArgumentException("Key not found: " + id);

        apiKeyMapper.revoke(id, LocalDateTime.now());
        invalidateCache(key.getKeyValue());  //使其从缓存中失效
        log.info("Revoked API key id={}, keyValue={}", id, maskKey(key.getKeyValue()));
    }

    public void deleteKey(Long id) {
        ApiKey key = apiKeyMapper.selectById(id);
        if (key == null) throw new IllegalArgumentException("Key not found: " + id);

        apiKeyMapper.deleteById(id);
        invalidateCache(key.getKeyValue());
        log.info("Deleted API key id={}", id);
    }

    public void recordUsage(Long id) {
        apiKeyMapper.updateLastUsedAt(id, LocalDateTime.now());
    }//记录API最后使用时间

    // ── 缓存操作方法  ─────────────────────────────────────────

    private void cacheKey(ApiKey key) {
        try {
            Map<String, Object> map = Map.of(
                    "id", key.getId(),
                    "permissions", key.getPermissions() != null ? key.getPermissions() : "",
                    "status", key.getStatus() != null ? key.getStatus() : "",
                    "rateLimit", key.getRateLimit() != null ? key.getRateLimit() : 20,
                    "deviceId", key.getDeviceId() != null ? key.getDeviceId() : ""
            );
            String json = objectMapper.writeValueAsString(map);
            String cacheKey = CACHE_KEY_PREFIX + key.getKeyValue();
            redisTemplate.opsForValue().set(cacheKey, json, CACHE_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.debug("Failed to cache API key to Redis", e);
        }
    }

    void invalidateCache(String keyValue) {
        try {
            redisTemplate.delete(CACHE_KEY_PREFIX + keyValue);
        } catch (Exception e) {
            log.debug("Failed to invalidate API key cache for keyValue, ignoring", e);
        }
    }

    // ── 私有方法 ───────────────────────────────────────

    private String generateKey() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private boolean isKeyUsable(ApiKey key) {
        if (!"ACTIVE".equals(key.getStatus())) return false;
        if (key.getExpiredAt() != null && key.getExpiredAt().isBefore(LocalDateTime.now())) return false;
        return true;
    }

    @SuppressWarnings("unchecked")
    private ApiKey mapToApiKey(Map<String, Object> map) { //不推荐使用Mapstruct代劳，它对Map的支持很有限。
        ApiKey key = new ApiKey();
        key.setId(map.get("id") != null ? ((Number) map.get("id")).longValue() : null);
        key.setPermissions((String) map.get("permissions"));
        key.setStatus((String) map.get("status"));
        key.setRateLimit(map.get("rateLimit") != null ? ((Number) map.get("rateLimit")).intValue() : 20);
        key.setDeviceId((String) map.get("deviceId"));
        key.setKeyValue(""); // not stored in cache for security
        return key;
    }

    private String maskKey(String key) {
        if (key == null || key.length() <= 8) return "***";
        return key.substring(0, 4) + "..." + key.substring(key.length() - 4);
    }
}
