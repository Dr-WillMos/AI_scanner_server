package org.example.aiscanner_server.service;

import org.example.aiscanner_server.mapper.DetectionRecordMapper;
import org.example.aiscanner_server.model.dto.HistoryFilter;
import org.example.aiscanner_server.model.dto.HistoryItem;
import org.example.aiscanner_server.model.dto.HistoryResponse;
import org.example.aiscanner_server.model.entity.DetectionRecord;
import org.example.aiscanner_server.model.enums.RiskLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class HistoryService {

    private static final Logger log = LoggerFactory.getLogger(HistoryService.class);
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final DetectionRecordMapper mapper;

    public HistoryService(DetectionRecordMapper mapper) {
        this.mapper = mapper;
    }

    public HistoryResponse query(String deviceId, Long afterId, int page, int size,
                                  String authorId, RiskLevel riskLevel,
                                  LocalDate startDate, LocalDate endDate) {
        if (deviceId == null || deviceId.isBlank()) {
            throw new IllegalArgumentException("deviceId 不能为空");
        }
        if (size < 1 || size > MAX_SIZE) {
            size = DEFAULT_SIZE;
        }
        if (page < 1) {
            page = 1;
        }

        HistoryFilter filter = HistoryFilter.of(deviceId, authorId, riskLevel, startDate, endDate);

        long total = mapper.countFiltered(filter);

        List<DetectionRecord> records;
        boolean hasMore;
        int effectivePage;

        if (afterId != null) {
            // Cursor-based incremental sync: fetch size+1 to detect hasMore
            records = mapper.selectFiltered(filter, afterId, size + 1);
            hasMore = records.size() > size;
            if (hasMore) {
                records = records.subList(0, size);
            }
            effectivePage = 1;
            log.info("历史查询(增量), filter=[authorId={}, riskLevel={}, {}~{}], afterId={}, 返回 {}/{} 条",
                    authorId, riskLevel, startDate, endDate, afterId, records.size(), total);
        } else {
            // Offset-based pagination
            int offset = (page - 1) * size;
            records = mapper.selectFilteredPaged(filter, offset, size);
            hasMore = (long) page * size < total;
            effectivePage = page;
            log.info("历史查询(分页), filter=[authorId={}, riskLevel={}, {}~{}], page={}, 返回 {}/{} 条",
                    authorId, riskLevel, startDate, endDate, page, records.size(), total);
        }

        List<HistoryItem> items = records.stream()
                .map(HistoryItem::from)
                .toList();

        return HistoryResponse.of(items, total, effectivePage, size, hasMore);
    }
}
