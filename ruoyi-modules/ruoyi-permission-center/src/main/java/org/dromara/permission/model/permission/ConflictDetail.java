package org.dromara.permission.model.permission;

import lombok.Data;

@Data
public class ConflictDetail {
    private Long firstOperationId;
    private Long secondOperationId;
    private Long resourceId;
    private Long conflictRuleId;
}
