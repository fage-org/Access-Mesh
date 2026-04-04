package org.dromara.permission.model.permission;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PermissionSnapshot {
    private Long tenantId;
    private Long abstractUserId;
    private Long bizDomainId;
    private String versionToken;
    private List<SnapshotEntry> entries = new ArrayList<>();
    private List<ConflictDetail> conflicts = new ArrayList<>();
}
