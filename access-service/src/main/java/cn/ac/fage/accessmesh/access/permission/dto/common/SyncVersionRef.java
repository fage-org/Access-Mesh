package cn.ac.fage.accessmesh.access.permission.dto.common;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * 同步事件版本引用
 * <p>
 * 由 occurredAt + sequenceNo 共同定位同步事件版本，用于
 * sync_metadata 中的旧版本 no-op 比较。详见 api-contract §6.2.2.4。
 * </p>
 */
public record SyncVersionRef(
        @NotNull LocalDateTime occurredAt,
        @NotNull Long sequenceNo
) {
}
