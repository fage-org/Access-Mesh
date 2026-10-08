package cn.ac.fage.accessmesh.access.tenant.dto;

import cn.ac.fage.accessmesh.access.tenant.entity.SysTenant;
import cn.ac.fage.accessmesh.common.security.TenantGateState;
import java.time.LocalDateTime;

public record TenantResp(Long id, String code, String name, Integer status, Long adminUserId,
                         String accessState, LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static TenantResp from(SysTenant row,TenantGateState gate) {
        boolean consistent = gate.sessionEpoch() == row.getSessionEpoch()
            && ((row.getStatus() == 1 && gate.status() == TenantGateState.Status.ENABLED)
                || (row.getStatus() == 0 && gate.status() == TenantGateState.Status.DISABLED));
        return new TenantResp(row.getId(), row.getCode(), row.getName(), row.getStatus(), row.getAdminUserId(),
            consistent ? gate.status().name() : "UNAVAILABLE", row.getCreatedAt(), row.getUpdatedAt());
    }
}
