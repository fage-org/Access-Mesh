package cn.ac.fage.accessmesh.permission.service.domain;

import java.util.List;

public interface PermissionChangeDomainService {

    void record(ChangeLogContext context, List<ChangeLogEntry> changes);

    record ChangeLogContext(
        Long tenantId,
        Long bizDomainId,
        Long operatorId,
        String requestId,
        String changeSource,
        String changeReason
    ) {}

    record ChangeLogEntry(
        String entityType,
        Long entityId,
        String operation,
        String oldSnapshot,
        String newSnapshot,
        String diffSnapshot,
        Long[] affectedUserIds,
        Long[] affectedRoleIds
    ) {}
}
