package org.example.aiscanner_server.controller;

import org.example.aiscanner_server.common.ApiResponse;
import org.example.aiscanner_server.model.dto.BlacklistAddRequest;
import org.example.aiscanner_server.service.BlacklistService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/blacklist")
public class BlacklistController {
/**  Controller层是为了和用户对接的层，而实际上的业务逻辑写在Service层  */
    private final BlacklistService blacklistService;

    public BlacklistController(BlacklistService blacklistService) {
        this.blacklistService = blacklistService;
    }

    // ── 权威 ──────────────────────────────────────────

    @GetMapping("/authority")
    public ApiResponse<Set<String>> listAuthority() {
        return ApiResponse.ok(blacklistService.listAuthority());
    }  //列出所有权威黑名单成员

    @GetMapping("/authority/{authorId}")
    public ApiResponse<Map<String, Boolean>> checkAuthority(@PathVariable String authorId) {  //这里转而调用黑名单Service的方法。
        return ApiResponse.ok(Map.of("blacklisted", blacklistService.isInAuthority(authorId)));
    }

    @PostMapping("/authority")
    public ApiResponse<Void> addToAuthority(@Valid @RequestBody BlacklistAddRequest req) {
        blacklistService.addToAuthority(req.authorId(), req.reason());
        return ApiResponse.ok();
    }

    @DeleteMapping("/authority/{authorId}")   //同上，注解本身只代表 HTTP 方法类型，Spring只会根据Http请求的类型的不同调用不同的方法
    public ApiResponse<Void> removeFromAuthority(@PathVariable String authorId) {
        blacklistService.removeFromAuthority(authorId);
        return ApiResponse.ok();
    }

    // ── 全局黑名单，代码逻辑和上面的权威黑名单实际上没有什么不同。 ─────────────────────────────────────────────

    @GetMapping("/global")
    public ApiResponse<Set<String>> listGlobal() {
        return ApiResponse.ok(blacklistService.listGlobal());
    }

    @GetMapping("/global/{authorId}")
    public ApiResponse<Map<String, Boolean>> checkGlobal(@PathVariable String authorId) {
        return ApiResponse.ok(Map.of("blacklisted", blacklistService.isInGlobal(authorId)));
    }

    @PostMapping("/global")
    public ApiResponse<Void> addToGlobal(@Valid @RequestBody BlacklistAddRequest req) {
        blacklistService.addToGlobal(req.authorId(), req.reason());
        return ApiResponse.ok();
    }

    @DeleteMapping("/global/{authorId}")
    public ApiResponse<Void> removeFromGlobal(@PathVariable String authorId) {
        blacklistService.removeFromGlobal(authorId);
        return ApiResponse.ok();
    }

    // ── 临时 ───────────────────────────────────────────────

    @GetMapping("/temp")
    public ApiResponse<Set<String>> listTemp() {
        return ApiResponse.ok(blacklistService.listTemp());
    }

    @GetMapping("/temp/{authorId}")   //在临时黑名单中查询发布者ID和剩余封禁时间的接口
    public ApiResponse<Map<String, Object>> checkTemp(@PathVariable String authorId) {
        boolean blacklisted = blacklistService.isInTemp(authorId);
        Long ttl = blacklisted ? blacklistService.getTempTtl(authorId) : null; //查询当前高危发布者在临时黑名单中的剩余封禁时间
        return ApiResponse.ok(Map.of("blacklisted", blacklisted, "ttlSeconds", ttl != null ? ttl : 0));
    }

    @PostMapping("/temp")    //管理员手动管理临时黑名单
    public ApiResponse<Void> addToTemp(@Valid @RequestBody BlacklistAddRequest req) {
        String reason = req.reason() != null ? req.reason() : "手动添加";
        blacklistService.addToTemp(req.authorId(), reason);
        return ApiResponse.ok();
    }

    @DeleteMapping("/temp/{authorId}")
    public ApiResponse<Void> removeFromTemp(@PathVariable String authorId) {
        blacklistService.removeFromTemp(authorId);
        return ApiResponse.ok();
    }

    // ── 查询 ─────────────────────────────

    @GetMapping("/check/{authorId}")//聚合查询三级黑名单的接口
    public ApiResponse<BlacklistService.BlacklistHit> checkAll(@PathVariable String authorId) {
        return ApiResponse.ok(blacklistService.checkBlacklist(authorId));
    }
}
