package org.example.aiscanner_server.model.entity;

import java.time.LocalDateTime;

public class BlacklistEntry {

    private Long id;
    private String authorId;
    private String listType;
    private String reason;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getAuthorId() { return authorId; }
    public void setAuthorId(String authorId) { this.authorId = authorId; }

    public String getListType() { return listType; }
    public void setListType(String listType) { this.listType = listType; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
