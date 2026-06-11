package org.example.aiscanner_server.controller;

import org.example.aiscanner_server.common.ApiResponse;
import org.example.aiscanner_server.model.dto.KeyCreateRequest;
import org.example.aiscanner_server.model.dto.KeyInfo;
import org.example.aiscanner_server.model.dto.KeyRegisterRequest;
import org.example.aiscanner_server.model.dto.KeyRegisterResponse;
import org.example.aiscanner_server.model.dto.KeyUpdateRequest;
import org.example.aiscanner_server.model.entity.ApiKey;
import org.example.aiscanner_server.service.ApiKeyService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import jakarta.validation.Valid;
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
    public ApiResponse<KeyRegisterResponse> register(@Valid @RequestBody KeyRegisterRequest req) {
        String deviceName = req.deviceName() != null ? req.deviceName() : req.deviceId();
        ApiKey apiKey = apiKeyService.registerKey(req.deviceId(), deviceName);
        return ApiResponse.ok(new KeyRegisterResponse(apiKey.getKeyValue(), apiKey.getExpiredAt()));
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
    public ApiResponse<KeyRegisterResponse> create(@RequestBody KeyCreateRequest req) {
        String keyName = req.keyName() != null ? req.keyName() : "手动创建";
        LocalDateTime expiredAt = req.expiredAt() != null ? LocalDateTime.parse(req.expiredAt()) : null;
        ApiKey apiKey = apiKeyService.createKey(keyName, req.permissions(), req.rateLimit(), expiredAt);
        return ApiResponse.ok(new KeyRegisterResponse(apiKey.getKeyValue(), apiKey.getExpiredAt()));
    }

    // ── 管理员：更新Key ────────────────────────────────────

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @RequestBody KeyUpdateRequest req) {
        LocalDateTime expiredAt = null;
        if (req.expiredAt() != null) {
            expiredAt = LocalDateTime.parse(req.expiredAt());
        }
        apiKeyService.updateKey(id, req.keyName(), req.permissions(), req.rateLimit(), expiredAt);
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
