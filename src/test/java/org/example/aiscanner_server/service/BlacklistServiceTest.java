package org.example.aiscanner_server.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BlacklistServiceTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private SetOperations<String, String> setOps;
    @Mock private ValueOperations<String, String> valueOps;

    @InjectMocks
    private BlacklistService service;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOps);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // ── checkBlacklist (priority: authority > global > temp) ──

    @Test
    @DisplayName("checkBlacklist: authority 命中优先返回")
    void checkBlacklistAuthorityFirst() {
        when(setOps.isMember("blacklist:authority", "a1")).thenReturn(true);

        var hit = service.checkBlacklist("a1");

        assertTrue(hit.hit());
        assertEquals("authority", hit.source());
        assertEquals("权威黑名单发布者", hit.reason());
        verify(setOps, never()).isMember(eq("blacklist:global"), anyString());
    }

    @Test
    @DisplayName("checkBlacklist: authority 未命中，global 命中")
    void checkBlacklistGlobalSecond() {
        when(setOps.isMember("blacklist:authority", "a2")).thenReturn(false);
        when(setOps.isMember("blacklist:global", "a2")).thenReturn(true);

        var hit = service.checkBlacklist("a2");

        assertTrue(hit.hit());
        assertEquals("global", hit.source());
        assertEquals("全局黑名单发布者", hit.reason());
    }

    @Test
    @DisplayName("checkBlacklist: authority/global 未命中，temp 命中")
    void checkBlacklistTempThird() {
        when(setOps.isMember("blacklist:authority", "a3")).thenReturn(false);
        when(setOps.isMember("blacklist:global", "a3")).thenReturn(false);
        when(valueOps.get("blacklist:temp:a3")).thenReturn("检测到高危行为");

        var hit = service.checkBlacklist("a3");

        assertTrue(hit.hit());
        assertEquals("temp", hit.source());
        assertTrue(hit.reason().contains("检测到高危行为"));
    }

    @Test
    @DisplayName("checkBlacklist: 全部未命中返回 miss")
    void checkBlacklistAllMiss() {
        when(setOps.isMember("blacklist:authority", "a4")).thenReturn(false);
        when(setOps.isMember("blacklist:global", "a4")).thenReturn(false);
        when(valueOps.get("blacklist:temp:a4")).thenReturn(null);

        var hit = service.checkBlacklist("a4");

        assertFalse(hit.hit());
        assertNull(hit.source());
        assertNull(hit.reason());
    }

    // ── Authority CRUD ──

    @Test
    @DisplayName("addToAuthority 向 SET 添加 authorId")
    void addToAuthority() {
        service.addToAuthority("a1");
        verify(setOps).add("blacklist:authority", "a1");
    }

    @Test
    @DisplayName("removeFromAuthority 从 SET 移除 authorId")
    void removeFromAuthority() {
        service.removeFromAuthority("a1");
        verify(setOps).remove("blacklist:authority", "a1");
    }

    @Test
    @DisplayName("listAuthority 返回所有成员")
    void listAuthority() {
        when(setOps.members("blacklist:authority")).thenReturn(Set.of("a1", "a2"));
        assertEquals(Set.of("a1", "a2"), service.listAuthority());
    }

    // ── Global CRUD ──

    @Test
    @DisplayName("addToGlobal 向 SET 添加 authorId")
    void addToGlobal() {
        service.addToGlobal("a1");
        verify(setOps).add("blacklist:global", "a1");
    }

    // ── Temp (24h TTL) ──

    @Test
    @DisplayName("addToTemp 设置带 24h TTL 的 key")
    void addToTemp() {
        service.addToTemp("a1", "高危");
        verify(valueOps).set(eq("blacklist:temp:a1"), eq("高危"),
                eq(86400L), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("addToTemp 默认 reason")
    void addToTempDefaultReason() {
        service.addToTemp("a1", null);
        verify(valueOps).set(eq("blacklist:temp:a1"), eq("触发高危检测"),
                eq(86400L), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("isInTemp 检查 key 是否存在")
    void isInTemp() {
        when(redisTemplate.hasKey("blacklist:temp:a1")).thenReturn(true);
        assertTrue(service.isInTemp("a1"));
    }

    @Test
    @DisplayName("removeFromTemp 删除 key")
    void removeFromTemp() {
        service.removeFromTemp("a1");
        verify(redisTemplate).delete("blacklist:temp:a1");
    }

    @Test
    @DisplayName("getTempTtl 返回剩余秒数")
    void getTempTtl() {
        when(redisTemplate.getExpire("blacklist:temp:a1", TimeUnit.SECONDS)).thenReturn(3600L);
        assertEquals(3600L, service.getTempTtl("a1"));
    }
}
