package org.example.aiscanner_server.service;

import org.example.aiscanner_server.mapper.DetectionRecordMapper;
import org.example.aiscanner_server.model.dto.HistoryFilter;
import org.example.aiscanner_server.model.entity.DetectionRecord;
import org.example.aiscanner_server.model.enums.RiskLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
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
        assertThrows(IllegalArgumentException.class,
                () -> service.query("", null, 1, 20, null, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> service.query(null, null, 1, 20, null, null, null, null));
    }

    @Test
    @DisplayName("size 超限时修正为默认值 20")
    void sizeClampingTooLarge() {
        when(mapper.countFiltered(any())).thenReturn(50L);
        when(mapper.selectFilteredPaged(any(), eq(0), eq(20))).thenReturn(List.of());

        service.query("d1", null, 1, 200, null, null, null, null);

        verify(mapper).selectFilteredPaged(any(), eq(0), eq(20));
    }

    @Test
    @DisplayName("page < 1 时修正为 1")
    void pageClamping() {
        when(mapper.countFiltered(any())).thenReturn(50L);
        when(mapper.selectFilteredPaged(any(), eq(0), eq(20))).thenReturn(List.of());

        service.query("d1", null, -5, 20, null, null, null, null);

        verify(mapper).selectFilteredPaged(any(), eq(0), eq(20));
    }

    @Test
    @DisplayName("分页模式：返回正确 page/size/total/hasMore")
    void paginationMode() {
        when(mapper.countFiltered(any())).thenReturn(25L);
        var records = List.of(makeRecord(10L, "d1", "a1", RiskLevel.HIGH, 0.8));
        when(mapper.selectFilteredPaged(any(), eq(0), eq(20))).thenReturn(records);

        var result = service.query("d1", null, 1, 20, null, null, null, null);

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
        when(mapper.countFiltered(any())).thenReturn(15L);
        when(mapper.selectFilteredPaged(any(), eq(0), eq(20)))
                .thenReturn(List.of(makeRecord(15L, "d1", "a1", RiskLevel.MEDIUM, 0.5)));

        var result = service.query("d1", null, 1, 20, null, null, null, null);

        assertFalse(result.isHasMore());
    }

    @Test
    @DisplayName("增量模式：afterId 游标拉取")
    void cursorMode() {
        when(mapper.countFiltered(any())).thenReturn(100L);
        var records = List.of(
                makeRecord(50L, "d1", "a1", RiskLevel.HIGH, 0.9),
                makeRecord(49L, "d1", "a2", RiskLevel.MEDIUM, 0.4),
                makeRecord(48L, "d1", "a3", RiskLevel.SAFE, 0.1)
        );
        when(mapper.selectFiltered(any(), eq(60L), eq(4))).thenReturn(records);

        var result = service.query("d1", 60L, 1, 3, null, null, null, null);

        assertEquals(3, result.getRecords().size());
        assertEquals(50L, result.getLatestId());
        assertFalse(result.isHasMore());
    }

    @Test
    @DisplayName("增量模式：hasMore=true 时截断多取的那条记录")
    void cursorModeHasMore() {
        when(mapper.countFiltered(any())).thenReturn(100L);
        var records = List.of(
                makeRecord(50L, "d1", "a1", RiskLevel.HIGH, 0.9),
                makeRecord(49L, "d1", "a2", RiskLevel.MEDIUM, 0.4),
                makeRecord(48L, "d1", "a3", RiskLevel.SAFE, 0.1),
                makeRecord(47L, "d1", "a4", RiskLevel.MEDIUM, 0.5)
        );
        when(mapper.selectFiltered(any(), eq(60L), eq(4))).thenReturn(records);

        var result = service.query("d1", 60L, 1, 3, null, null, null, null);

        assertEquals(3, result.getRecords().size());
        assertTrue(result.isHasMore());
    }

    @Test
    @DisplayName("空结果集 latestId 为 null")
    void emptyResultHasNullLatestId() {
        when(mapper.countFiltered(any())).thenReturn(0L);
        when(mapper.selectFilteredPaged(any(), eq(0), eq(20))).thenReturn(Collections.emptyList());

        var result = service.query("d1", null, 1, 20, null, null, null, null);

        assertTrue(result.getRecords().isEmpty());
        assertNull(result.getLatestId());
        assertFalse(result.isHasMore());
    }

    @Test
    @DisplayName("按 riskLevel 筛选")
    void filterByRiskLevel() {
        when(mapper.countFiltered(any())).thenReturn(3L);
        var records = List.of(makeRecord(5L, "d1", "a1", RiskLevel.HIGH, 0.9));
        when(mapper.selectFilteredPaged(any(), eq(0), eq(20))).thenReturn(records);

        var result = service.query("d1", null, 1, 20, null, RiskLevel.HIGH, null, null);

        assertEquals(3L, result.getTotal());
        assertEquals(RiskLevel.HIGH, result.getRecords().get(0).getRiskLevel());
    }

    @Test
    @DisplayName("按 authorId 筛选")
    void filterByAuthorId() {
        when(mapper.countFiltered(any())).thenReturn(1L);
        var records = List.of(makeRecord(10L, "d1", "suspect123", RiskLevel.HIGH, 1.0));
        when(mapper.selectFilteredPaged(any(), eq(0), eq(20))).thenReturn(records);

        var result = service.query("d1", null, 1, 20, "suspect123", null, null, null);

        assertEquals(1L, result.getTotal());
        assertEquals("suspect123", result.getRecords().get(0).getAuthorId());
    }

    @Test
    @DisplayName("按日期范围筛选")
    void filterByDateRange() {
        when(mapper.countFiltered(any())).thenReturn(5L);
        when(mapper.selectFilteredPaged(any(), eq(0), eq(20))).thenReturn(List.of());

        var result = service.query("d1", null, 1, 20, null, null,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 6));

        assertEquals(5L, result.getTotal());
        assertTrue(result.getRecords().isEmpty());
    }

    @Test
    @DisplayName("组合筛选：authorId + riskLevel + 日期")
    void combinedFilters() {
        when(mapper.countFiltered(any())).thenReturn(2L);
        when(mapper.selectFilteredPaged(any(), eq(0), eq(20))).thenReturn(List.of());

        var result = service.query("d1", null, 1, 20, "suspect", RiskLevel.HIGH,
                LocalDate.of(2026, 6, 1), null);

        assertEquals(2L, result.getTotal());
    }
}
