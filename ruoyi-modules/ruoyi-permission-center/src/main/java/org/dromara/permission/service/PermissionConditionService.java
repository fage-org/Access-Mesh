package org.dromara.permission.service;

import org.dromara.permission.domain.dto.ConditionListReq;
import org.dromara.permission.domain.dto.ConditionSaveReq;
import org.dromara.permission.domain.dto.ConditionUpdateReq;
import org.dromara.permission.domain.dto.IdsReq;
import org.dromara.permission.domain.vo.PermissionConditionVo;

import java.util.List;

/**
 * 生效条件 permission_condition 服务
 */
public interface PermissionConditionService {

    List<PermissionConditionVo> list(ConditionListReq req);

    void save(ConditionSaveReq req);

    void update(Long conditionId, ConditionUpdateReq req);

    void remove(IdsReq req);
}
