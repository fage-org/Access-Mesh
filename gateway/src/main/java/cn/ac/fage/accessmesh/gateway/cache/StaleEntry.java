package cn.ac.fage.accessmesh.gateway.cache;

import cn.ac.fage.accessmesh.perm.common.dto.resp.InterfaceSnapshotResp;

import java.time.Instant;
import java.util.Objects;

/**
 * 陈旧接口快照条目。
 *
 * @param snapshot   原始接口快照
 * @param staleUntil 允许 stale-allow 续命的截止时间
 */
public record StaleEntry(InterfaceSnapshotResp snapshot, Instant staleUntil) {

    public StaleEntry {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        Objects.requireNonNull(staleUntil, "staleUntil must not be null");
    }
}
