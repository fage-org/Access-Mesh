package org.dromara.permission.domain.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 依赖缺口明细
 */
@Data
public class DependencyCheckGapVo {
    private Long resourceEntityId;
    private Long operationPermissionId;
    private List<DependencyPathNodeVo> path = new ArrayList<>();
}
