package org.dromara.permission.domain.vo;

import lombok.Data;

import java.util.List;

@Data
public class PermissionSnapshotVo {
    private Long tenantId;
    private Long abstractUserId;
    private Long bizDomainId;
    private String versionToken;
    private List<PermissionSnapshotEntryVo> entries;
    private List<PermissionCheckConflictVo> conflicts;
}
