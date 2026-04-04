package org.dromara.permission.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PermissionCheckVo {
    private Boolean allowed;
    private String reason;
    private List<PermissionCheckGrantedByVo> grantedBy;
    private List<PermissionCheckConflictVo> conflicts;
    private List<PermissionCheckDependencyGapVo> dependencyGaps;

    public static PermissionCheckVo allow() {
        return new PermissionCheckVo(true, null, null, null, null);
    }

    public static PermissionCheckVo deny(String reason) {
        return new PermissionCheckVo(false, reason, null, null, null);
    }
}
