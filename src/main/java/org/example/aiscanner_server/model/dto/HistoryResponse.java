package org.example.aiscanner_server.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class HistoryResponse {

    private List<HistoryItem> records;
    private long total;
    private int page;
    private int size;
    private boolean hasMore;
    private Long latestId;

    private HistoryResponse() {}

    public static HistoryResponse of(List<HistoryItem> records, long total, int page, int size, boolean hasMore) {
        HistoryResponse r = new HistoryResponse();
        r.records = records;
        r.total = total;
        r.page = page;
        r.size = size;
        r.hasMore = hasMore;
        r.latestId = records.isEmpty() ? null : records.get(0).getId();
        return r;
    }

    public List<HistoryItem> getRecords() { return records; }
    public long getTotal() { return total; }
    public int getPage() { return page; }
    public int getSize() { return size; }
    public boolean isHasMore() { return hasMore; }
    public Long getLatestId() { return latestId; }
}
