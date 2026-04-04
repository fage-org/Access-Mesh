package org.dromara.permission.domain.vo;

import lombok.Data;

@Data
public class PermissionCheckDependencyGapVo {
    private Long resourceEntityId;
    private Long operationPermissionId;
}
