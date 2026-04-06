package org.dromara.permission.service;

import org.dromara.permission.domain.dto.ConflictDetectReq;
import org.dromara.permission.domain.dto.ConflictRuleListReq;
import org.dromara.permission.domain.dto.ConflictRuleSaveReq;
import org.dromara.permission.domain.dto.IdsReq;
import org.dromara.permission.domain.vo.ConflictDetectionPageVo;
import org.dromara.permission.domain.vo.ConflictRuleVo;
import org.dromara.permission.domain.vo.ConflictViolationVo;

import java.util.List;

/**
 * 权限冲突规则与检测服务
 */
public interface ConflictRuleService {

    List<ConflictRuleVo> list(ConflictRuleListReq req);

    void save(ConflictRuleSaveReq req);

    void remove(IdsReq req);

    List<ConflictViolationVo> detect(ConflictDetectReq req);

    ConflictDetectionPageVo detectPage(ConflictDetectReq req);
}
