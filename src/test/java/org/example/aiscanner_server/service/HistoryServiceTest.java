package org.example.aiscanner_server.service;

import org.example.aiscanner_server.mapper.DetectionRecordMapper;
import org.example.aiscanner_server.model.entity.DetectionRecord;
import org.example.aiscanner_server.model.enums.RiskLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HistoryServiceTest {

    @Mock private DetectionRecordMapper mapper;

    private HistoryService service;

    @BeforeEach
    void setUp() {
        service = new HistoryService(mapper);
    }

    private DetectionRecord makeRecord(long id, String deviceId, String authorId, RiskLevel level, double score) {
        DetectionRecord r = new DetectionRecord();
        r.setId(id);
        r.setDeviceId(deviceId);
        r.setAuthorId(authorId);
        r.setRiskLevel(level);
        r.setScore(score);
        r.setCreatedAt(LocalDateTime.now());
        return r;
    }

    @Test
    @DisplayName("deviceId 为空时抛异常")
    void emptyDeviceIdThrows() {
        assertThrows(IllegalArgumentException.class, () -> service.query("", null, 1, 20));
        assertThrows(IllegalArgumentException.class, () -> service.query(null, null, 1, 20));
    }

    @Test
    @DisplayName("size 超限时修正为默认值 20")
    void sizeClampingTooLarge() {
        when(mapper.countByDeviceId("d1")).thenReturn(50L);
        when(mapper.selectByDeviceIdPaged(eq("d1"), eq(0), eq(20))).thenReturn(List.of());

        service.query("d1", null, 1, 200);

        verify(mapper).selectByDeviceIdPaged("d1", 0, 20);
    }

    @Test
    @DisplayName("page < 1 时修正为 1")
    void pageClamping() {
        when(mapper.countByDeviceId("d1")).thenReturn(50L);
        when(mapper.selectByDeviceIdPaged(eq("d1"), eq(0), eq(20))).thenReturn(List.of());

        service.query("d1", null, -5, 20);

        verify(mapper).selectByDeviceIdPaged("d1", 0, 20);
    }

    @Test
    @DisplayName("分页模式：返回正确 page/size/total/hasMore")
    void paginationMode() {
        when(mapper.countByDeviceId("d1")).thenReturn(25L);
        var records = List.of(makeRecord(10L, "d1", "a1", RiskLevel.HIGH, 0.8));
        when(mapper.selectByDeviceIdPaged("d1", 0, 20)).thenReturn(records);

        var result = service.query("d1", null, 1, 20);

        assertEquals(1, result.getRecords().size());
        assertEquals(25L, result.getTotal());
        assertEquals(1, result.getPage());
        assertEquals(20, result.getSize());
        assertTrue(result.isHasMore());
        assertEquals(10L, result.getLatestId());
    }

    @Test
    @DisplayName("分页模式：最后一页 hasMore=false")
    void paginationLastPage() {
        when(mapper.countByDeviceId("d1")).thenReturn(15L);
        when(mapper.selectByDeviceIdPaged("d1", 0, 20)).thenReturn(
                List.of(makeRecord(15L, "d1", "a1", RiskLevel.MEDIUM, 0.5)));

        var result = service.query("d1", null, 1, 20);

        assertFalse(result.isHasMore());
    }

    @Test
    @DisplayName("增量模式：afterId 游标拉取")
    void cursorMode() {
        when(mapper.countByDeviceId("d1")).thenReturn(100L);
        var records = List.of(
                makeRecord(50L, "d1", "a1", RiskLevel.HIGH, 0.9),
                makeRecord(49L, "d1", "a2", RiskLevel.MEDIUM, 0.4),
                makeRecord(48L, "d1", "a3", RiskLevel.SAFE, 0.1)
        );
        when(mapper.selectByDeviceIdAfterId("d1", 60L, 4)).thenReturn(records);

        var result = service.query("d1", 60L, 1, 3);

        assertEquals(3, result.getRecords().size());
        assertEquals(50L, result.getLatestId());
        assertFalse(result.isHasMore());
        assertEquals(1, result.getPage());
    }

    @Test
    @DisplayName("增量模式：hasMore=true 时截断多取的那条记录")
    void cursorModeHasMore() {
        when(mapper.countByDeviceId("d1")).thenReturn(100L);
        var records = List.of(
                makeRecord(50L, "d1", "a1", RiskLevel.HIGH, 0.9),
                makeRecord(49L, "d1", "a2", RiskLevel.MEDIUM, 0.4),
                makeRecord(48L, "d1", "a3", RiskLevel.SAFE, 0.1),
                makeRecord(47L, "d1", "a4", RiskLevel.MEDIUM, 0.5)
        );
        when(mapper.selectByDeviceIdAfterId("d1", 60L, 4)).thenReturn(records);

        var result = service.query("d1", 60L, 1, 3);

        assertEquals(3, result.getRecords().size());
        assertTrue(result.isHasMore());
    }

    @Test
    @DisplayName("空结果集 latestId 为 null")
    void emptyResultHasNullLatestId() {
        when(mapper.countByDeviceId("d1")).thenReturn(0L);
        when(mapper.selectByDeviceIdPaged("d1", 0, 20)).thenReturn(Collections.emptyList());

        var result = service.query("d1", null, 1, 20);

        assertTrue(result.getRecords().isEmpty());
        assertNull(result.getLatestId());
        assertFalse(result.isHasMore());
    }
}
