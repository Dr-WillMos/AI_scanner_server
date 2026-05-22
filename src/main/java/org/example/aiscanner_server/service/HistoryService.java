package org.example.aiscanner_server.service;

import org.example.aiscanner_server.mapper.DetectionRecordMapper;
import org.example.aiscanner_server.model.dto.HistoryItem;
import org.example.aiscanner_server.model.dto.HistoryResponse;
import org.example.aiscanner_server.model.entity.DetectionRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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

    public HistoryResponse query(String deviceId, Long afterId, int page, int size) {
        if (deviceId == null || deviceId.isBlank()) {
            throw new IllegalArgumentException("deviceId 不能为空");
        }
        if (size < 1 || size > MAX_SIZE) {
            size = DEFAULT_SIZE;
        }
        if (page < 1) {
            page = 1;
        }

        long total = mapper.countByDeviceId(deviceId);

        List<DetectionRecord> records;
        boolean hasMore;
        int effectivePage;

        if (afterId != null) {
            // Cursor-based incremental sync: fetch size+1 to detect hasMore
            records = mapper.selectByDeviceIdAfterId(deviceId, afterId, size + 1);
            hasMore = records.size() > size;
            if (hasMore) {
                records = records.subList(0, size);
            }
            effectivePage = 1;
            log.info("历史查询(增量), deviceId={}, afterId={}, 返回 {}/{} 条",
                    deviceId, afterId, records.size(), total);
        } else {
            // Offset-based pagination
            int offset = (page - 1) * size;
            records = mapper.selectByDeviceIdPaged(deviceId, offset, size);
            hasMore = (long) page * size < total;
            effectivePage = page;
            log.info("历史查询(分页), deviceId={}, page={}/{}, 返回 {}/{} 条",
                    deviceId, page, (total + size - 1) / size, records.size(), total);
        }

        List<HistoryItem> items = records.stream()
                .map(HistoryItem::from)
                .toList();

        return HistoryResponse.of(items, total, effectivePage, size, hasMore);
    }
}
