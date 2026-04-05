package org.dromara.permission.domain.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PermissionCheckVo {
    private Boolean granted;
    private String denyReason;
    private List<PermissionCheckGrantedByVo> grantedBy;
    private List<PermissionCheckConflictVo> conflicts;
    private List<PermissionCheckDependencyGapVo> dependencyGaps;

    public static PermissionCheckVo allow() {
        return new PermissionCheckVo(true, null, null, null, null);
    }

    public static PermissionCheckVo deny(String denyReason) {
        return new PermissionCheckVo(false, denyReason, null, null, null);
    }

    @JsonIgnore
    public Boolean getAllowed() {
        return granted;
    }

    @JsonIgnore
    public void setAllowed(Boolean allowed) {
        this.granted = allowed;
    }

    @JsonIgnore
    public String getReason() {
        return denyReason;
    }

    @JsonIgnore
    public void setReason(String reason) {
        this.denyReason = reason;
    }
}
