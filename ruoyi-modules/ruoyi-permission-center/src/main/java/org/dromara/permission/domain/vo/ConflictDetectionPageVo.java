package org.dromara.permission.domain.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 冲突检测分页结果
 */
@Data
public class ConflictDetectionPageVo {
    private long total;
    private int pageNum;
    private int pageSize;
    private List<ConflictViolationVo> items = new ArrayList<>();
}
