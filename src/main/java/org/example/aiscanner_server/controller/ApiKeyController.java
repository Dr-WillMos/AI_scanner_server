package org.example.aiscanner_server.controller;

import org.example.aiscanner_server.common.ApiResponse;
import org.example.aiscanner_server.model.dto.KeyInfo;
import org.example.aiscanner_server.model.dto.KeyRegisterResponse;
import org.example.aiscanner_server.model.entity.ApiKey;
import org.example.aiscanner_server.service.ApiKeyService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
 /* 本Controller用于提供认证支持 */
@RestController
@RequestMapping("/api/v1/keys")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    public ApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    // ── 设备注册 ──────────────────────────────────

    @PostMapping("/register")
    public ApiResponse<KeyRegisterResponse> register(@RequestBody Map<String, String> body) {
        String deviceId = body.get("deviceId");
        if (deviceId == null || deviceId.isBlank()) {
            return ApiResponse.error(400, "deviceId 不能为空");
        }
        String deviceName = body.getOrDefault("deviceName", deviceId);  //Name是昵称，后者是ID识别符
        ApiKey apiKey = apiKeyService.registerKey(deviceId, deviceName);
        return ApiResponse.ok(new KeyRegisterResponse(apiKey.getKeyValue(), apiKey.getExpiredAt())); //返回注册后的key
    }

    // ── 管理员：查询所有的已注册Key─────────────────────────────────

    @GetMapping
    public ApiResponse<List<KeyInfo>> listAll() {
        List<KeyInfo> keys = apiKeyService.listAll().stream()
                .map(k -> KeyInfo.forList(k.getId(), k.getKeyName(), k.getDeviceId(),
                        k.getPermissions(), k.getStatus(), k.getRateLimit(),
                        k.getLastUsedAt(), k.getExpiredAt(), k.getCreatedAt()))
                .toList();  //通过列举给出一切必要信息
        return ApiResponse.ok(keys);
    }

    // ── 管理员：查询单个ID的信息 ────────────────────────────────

    @GetMapping("/{id}")
    public ApiResponse<KeyInfo> getById(@PathVariable Long id) {
        Optional<ApiKey> opt = apiKeyService.getById(id);
        if (opt.isEmpty()) return ApiResponse.error(404, "Key not found");
        ApiKey k = opt.get();
        return ApiResponse.ok(KeyInfo.full(k.getId(), k.getKeyValue(), k.getKeyName(),
                k.getDeviceId(), k.getPermissions(), k.getStatus(), k.getRateLimit(),
                k.getLastUsedAt(), k.getExpiredAt(), k.getCreatedAt(), k.getRevokedAt()));
    }  //这里写了十一个传参，哈人。

    // ── 管理员：创建key ────────────────────────────────────

    @PostMapping
    public ApiResponse<KeyRegisterResponse> create(@RequestBody Map<String, Object> body) {
        String keyName = (String) body.getOrDefault("keyName", "手动创建");  //可以指定Key备注，如用途等
        String permissions = (String) body.get("permissions");  //获取权限字符
        Integer rateLimit = body.get("rateLimit") != null ? ((Number) body.get("rateLimit")).intValue() : null; //限流级，该Key单位时间内能调用的次数
        LocalDateTime expiredAt = null;
        if (body.get("expiredAt") != null) {
            expiredAt = LocalDateTime.parse((String) body.get("expiredAt"));
        }   //过期时间，如果不设定就是永不过期。
        ApiKey apiKey = apiKeyService.createKey(keyName, permissions, rateLimit, expiredAt);
        return ApiResponse.ok(new KeyRegisterResponse(apiKey.getKeyValue(), apiKey.getExpiredAt()));
    }

    // ── 管理员：更新Key ────────────────────────────────────

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String keyName = (String) body.get("keyName");
        String permissions = (String) body.get("permissions");
        Integer rateLimit = body.get("rateLimit") != null ? ((Number) body.get("rateLimit")).intValue() : null;
        LocalDateTime expiredAt = null;
        if (body.containsKey("expiredAt") && body.get("expiredAt") != null) {
            expiredAt = LocalDateTime.parse((String) body.get("expiredAt"));
        } else if (body.containsKey("expiredAt") && body.get("expiredAt") == null) {
            expiredAt = null; // 允许设置为永不过期
        }
        apiKeyService.updateKey(id, keyName, permissions, rateLimit, expiredAt);
        return ApiResponse.ok();
    }

    // ── 管理员：撤回Key(可逆) ────────────────────────────────────

    @PostMapping("/{id}/revoke")
    public ApiResponse<Void> revoke(@PathVariable Long id) {
        apiKeyService.revokeKey(id);
        return ApiResponse.ok();
    }

    // ── 管理员：删除Key(不可逆) ────────────────────────────────────

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        apiKeyService.deleteKey(id);
        return ApiResponse.ok();
    }
}
