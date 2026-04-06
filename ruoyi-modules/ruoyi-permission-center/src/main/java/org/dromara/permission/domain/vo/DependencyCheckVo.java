package org.dromara.permission.domain.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 依赖检查结果
 */
@Data
public class DependencyCheckVo {
    private boolean satisfied;
    private List<DependencyCheckGapVo> gaps = new ArrayList<>();
}
