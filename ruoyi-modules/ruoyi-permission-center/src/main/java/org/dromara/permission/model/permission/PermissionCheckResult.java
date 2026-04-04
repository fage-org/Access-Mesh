package org.dromara.permission.model.permission;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PermissionCheckResult {
    private boolean granted;
    private DenyReason denyReason;
    private List<MatchedPermission> grantedBy = new ArrayList<>();
    private List<ConflictDetail> conflicts = new ArrayList<>();
    private List<DependencyGap> dependencyGaps = new ArrayList<>();

    public static PermissionCheckResult granted(List<MatchedPermission> grantedBy, List<ConflictDetail> conflicts) {
        PermissionCheckResult result = new PermissionCheckResult();
        result.setGranted(true);
        result.setGrantedBy(grantedBy);
        result.setConflicts(conflicts);
        return result;
    }

    public static PermissionCheckResult denied(DenyReason reason, List<ConflictDetail> conflicts, List<DependencyGap> dependencyGaps) {
        PermissionCheckResult result = new PermissionCheckResult();
        result.setGranted(false);
        result.setDenyReason(reason);
        result.setConflicts(conflicts == null ? new ArrayList<>() : conflicts);
        result.setDependencyGaps(dependencyGaps == null ? new ArrayList<>() : dependencyGaps);
        return result;
    }
}
