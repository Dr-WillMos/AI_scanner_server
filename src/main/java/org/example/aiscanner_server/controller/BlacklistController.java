package org.example.aiscanner_server.controller;

import org.example.aiscanner_server.common.ApiResponse;
import org.example.aiscanner_server.service.BlacklistService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/blacklist")
public class BlacklistController {

    private final BlacklistService blacklistService;

    public BlacklistController(BlacklistService blacklistService) {
        this.blacklistService = blacklistService;
    }

    // ── Authority ──────────────────────────────────────────

    @GetMapping("/authority")
    public ApiResponse<Set<String>> listAuthority() {
        return ApiResponse.ok(blacklistService.listAuthority());
    }

    @GetMapping("/authority/{authorId}")
    public ApiResponse<Map<String, Boolean>> checkAuthority(@PathVariable String authorId) {
        return ApiResponse.ok(Map.of("blacklisted", blacklistService.isInAuthority(authorId)));
    }

    @PostMapping("/authority")
    public ApiResponse<Void> addToAuthority(@RequestBody Map<String, String> body) {
        String authorId = body.get("authorId");
        if (authorId == null || authorId.isBlank()) {
            return ApiResponse.error(400, "authorId 不能为空");
        }
        blacklistService.addToAuthority(authorId);
        return ApiResponse.ok();
    }

    @DeleteMapping("/authority/{authorId}")
    public ApiResponse<Void> removeFromAuthority(@PathVariable String authorId) {
        blacklistService.removeFromAuthority(authorId);
        return ApiResponse.ok();
    }

    // ── Global ─────────────────────────────────────────────

    @GetMapping("/global")
    public ApiResponse<Set<String>> listGlobal() {
        return ApiResponse.ok(blacklistService.listGlobal());
    }

    @GetMapping("/global/{authorId}")
    public ApiResponse<Map<String, Boolean>> checkGlobal(@PathVariable String authorId) {
        return ApiResponse.ok(Map.of("blacklisted", blacklistService.isInGlobal(authorId)));
    }

    @PostMapping("/global")
    public ApiResponse<Void> addToGlobal(@RequestBody Map<String, String> body) {
        String authorId = body.get("authorId");
        if (authorId == null || authorId.isBlank()) {
            return ApiResponse.error(400, "authorId 不能为空");
        }
        blacklistService.addToGlobal(authorId);
        return ApiResponse.ok();
    }

    @DeleteMapping("/global/{authorId}")
    public ApiResponse<Void> removeFromGlobal(@PathVariable String authorId) {
        blacklistService.removeFromGlobal(authorId);
        return ApiResponse.ok();
    }

    // ── Temp ───────────────────────────────────────────────

    @GetMapping("/temp")
    public ApiResponse<Set<String>> listTemp() {
        return ApiResponse.ok(blacklistService.listTemp());
    }

    @GetMapping("/temp/{authorId}")
    public ApiResponse<Map<String, Object>> checkTemp(@PathVariable String authorId) {
        boolean blacklisted = blacklistService.isInTemp(authorId);
        Long ttl = blacklisted ? blacklistService.getTempTtl(authorId) : null;
        return ApiResponse.ok(Map.of("blacklisted", blacklisted, "ttlSeconds", ttl != null ? ttl : 0));
    }

    @PostMapping("/temp")
    public ApiResponse<Void> addToTemp(@RequestBody Map<String, String> body) {
        String authorId = body.get("authorId");
        if (authorId == null || authorId.isBlank()) {
            return ApiResponse.error(400, "authorId 不能为空");
        }
        String reason = body.getOrDefault("reason", "手动添加");
        blacklistService.addToTemp(authorId, reason);
        return ApiResponse.ok();
    }

    @DeleteMapping("/temp/{authorId}")
    public ApiResponse<Void> removeFromTemp(@PathVariable String authorId) {
        blacklistService.removeFromTemp(authorId);
        return ApiResponse.ok();
    }

    // ── Convenience: check all ─────────────────────────────

    @GetMapping("/check/{authorId}")
    public ApiResponse<BlacklistService.BlacklistHit> checkAll(@PathVariable String authorId) {
        return ApiResponse.ok(blacklistService.checkBlacklist(authorId));
    }
}
