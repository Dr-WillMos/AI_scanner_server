package org.example.aiscanner_server.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class KeyInfo {

    private Long id;
    private String keyValue;
    private String keyName;
    private String deviceId;
    private String permissions;
    private String status;
    private Integer rateLimit;
    private LocalDateTime lastUsedAt;
    private LocalDateTime expiredAt;
    private LocalDateTime createdAt;
    private LocalDateTime revokedAt;

    public KeyInfo(Long id, String keyValue, String keyName, String deviceId,
                   String permissions, String status, Integer rateLimit,
                   LocalDateTime lastUsedAt, LocalDateTime expiredAt,
                   LocalDateTime createdAt, LocalDateTime revokedAt) {
        this.id = id;
        this.keyValue = keyValue;
        this.keyName = keyName;
        this.deviceId = deviceId;
        this.permissions = permissions;
        this.status = status;
        this.rateLimit = rateLimit;
        this.lastUsedAt = lastUsedAt;
        this.expiredAt = expiredAt;
        this.createdAt = createdAt;
        this.revokedAt = revokedAt;
    }

    // KeyValue omitted for list views
    public static KeyInfo forList(Long id, String keyName, String deviceId,
                                  String permissions, String status, Integer rateLimit,
                                  LocalDateTime lastUsedAt, LocalDateTime expiredAt,
                                  LocalDateTime createdAt) {
        return new KeyInfo(id, null, keyName, deviceId, permissions, status,
                rateLimit, lastUsedAt, expiredAt, createdAt, null);
    }

    // Full info including keyValue for detail views
    public static KeyInfo full(Long id, String keyValue, String keyName, String deviceId,
                                String permissions, String status, Integer rateLimit,
                                LocalDateTime lastUsedAt, LocalDateTime expiredAt,
                                LocalDateTime createdAt, LocalDateTime revokedAt) {
        return new KeyInfo(id, keyValue, keyName, deviceId, permissions, status,
                rateLimit, lastUsedAt, expiredAt, createdAt, revokedAt);
    }

    public Long getId() { return id; }
    public String getKeyValue() { return keyValue; }
    public String getKeyName() { return keyName; }
    public String getDeviceId() { return deviceId; }
    public String getPermissions() { return permissions; }
    public String getStatus() { return status; }
    public Integer getRateLimit() { return rateLimit; }
    public LocalDateTime getLastUsedAt() { return lastUsedAt; }
    public LocalDateTime getExpiredAt() { return expiredAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getRevokedAt() { return revokedAt; }
}
