package cn.ac.fage.accessmesh.access.sync.dto;

import java.time.LocalDateTime;

/** 已应用元数据与资源发布状态的 scope 视图，不表示最近一次尝试成败。 */
public record SyncStatusResp(String entityKind, String sourceService, String scopeKey,
                             long trackedItems, String maxGeneration, String lastFullGeneration,
                             String lastFullStatus, LocalDateTime updatedAt) {}
