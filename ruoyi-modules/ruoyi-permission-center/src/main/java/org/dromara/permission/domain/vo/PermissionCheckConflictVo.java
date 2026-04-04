package org.dromara.permission.domain.vo;

import lombok.Data;

@Data
public class PermissionCheckConflictVo {
    private Long firstOperationId;
    private Long secondOperationId;
    private Long resourceId;
    private Long conflictRuleId;
}
