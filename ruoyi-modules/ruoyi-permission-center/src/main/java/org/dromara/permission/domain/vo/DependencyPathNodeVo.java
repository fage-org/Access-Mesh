package org.dromara.permission.domain.vo;

import lombok.Data;

/**
 * 依赖路径节点
 */
@Data
public class DependencyPathNodeVo {
    private Long resourceEntityId;
    private Long operationPermissionId;
}
