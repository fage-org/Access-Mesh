package org.dromara.permission.domain.vo;

import lombok.Data;

/**
 * 冲突检测违规项
 */
@Data
public class ConflictViolationVo {
    private Long conflictRuleId;
    private Long abstractUserId;
    private Long abstractRoleId;
    private Long bizDomainId;
    private Long resourceEntityId;
    private Integer resourceTypeValue;
    private Long firstOperationPermissionId;
    private Long secondOperationPermissionId;
    private String description;
}
