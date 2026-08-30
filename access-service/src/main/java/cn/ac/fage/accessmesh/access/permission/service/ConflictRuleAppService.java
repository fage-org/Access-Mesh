package cn.ac.fage.accessmesh.access.permission.service;

import cn.ac.fage.accessmesh.access.permission.dto.req.ConflictRuleDetectReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.ConflictRuleReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.ConflictRuleUpdateReq;
import cn.ac.fage.accessmesh.access.permission.dto.resp.ConflictDetectResp;
import cn.ac.fage.accessmesh.access.permission.dto.resp.ConflictRuleResp;
import cn.ac.fage.accessmesh.common.exception.BizException;

import java.util.List;

/**
 * 权限冲突规则管理服务接口
 * <p>
 * 提供冲突规则的CRUD操作和冲突检测功能。
 * 冲突规则用于检测和处理权限冲突场景，如权限重叠、冲突操作等。
 * </p>
 */
public interface ConflictRuleAppService {

    /**
     * 创建冲突规则
     * <p>
     * 创建新的冲突规则实体，设置规则类型、检测条件等属性。
     * </p>
     *
     * @param tenantId   租户ID
     * @param req        冲突规则创建请求
     * @param operatorId 操作者ID
     * @return 创建的规则详情
     */
    ConflictRuleResp createConflictRule(Long tenantId, ConflictRuleReq req, Long operatorId);

    /**
     * 获取冲突规则详情
     * <p>
     * 根据规则ID查询冲突规则详情。
     * </p>
     *
     * @param tenantId 租户ID
     * @param ruleId   规则ID
     * @return 规则详情
     * @throws SecurityException 无 CONFLICT_RULE:VIEW 类型级权限时抛出（T-PERM-030）
     * @throws BizException      规则不存在（20020，T-PERM-030 从 data=null 宽松语义收紧）
     */
    ConflictRuleResp getConflictRule(Long tenantId, Long ruleId);

    /**
     * 查询冲突规则列表
     * <p>
     * 获取租户的所有冲突规则列表。全量不分页（量小，对齐 permission-condition/domain-config 定案）。
     * 类型级 CONFLICT_RULE:VIEW 门禁（T-PERM-030）。
     * </p>
     *
     * @param tenantId 租户ID
     * @return 规则列表
     * @throws SecurityException 无 CONFLICT_RULE:VIEW 类型级权限时抛出（T-PERM-030）
     */
    List<ConflictRuleResp> listConflictRules(Long tenantId);

    /**
     * 更新冲突规则
     * <p>
     * 更新冲突规则的基本信息和检测条件（全量替换语义，见 ConflictRuleUpdateReq）。
     * </p>
     *
     * @param tenantId   租户ID
     * @param req        规则更新请求
     * @param operatorId 操作者ID
     * @return 更新后的规则详情
     * @throws SecurityException 无 CONFLICT_RULE:UPDATE 类型级权限时抛出（T-PERM-030 收窄）
     * @throws BizException      规则不存在（20020）、字段校验失败或等价规则已存在（20032）时抛出
     */
    ConflictRuleResp updateConflictRule(Long tenantId, ConflictRuleUpdateReq req, Long operatorId);

    /**
     * 检测权限冲突
     * <p>
     * 根据冲突规则检测指定场景是否存在权限冲突。
     * 返回检测到的冲突详情。
     * 类型级 CONFLICT_RULE:VIEW 门禁（T-PERM-030，与 list/detail 同款——matchedRules 同样透出规则数据）。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      冲突检测请求，指定检测范围和条件
     * @return 冲突检测响应
     * @throws SecurityException 无 CONFLICT_RULE:VIEW 类型级权限时抛出（T-PERM-030）
     */
    ConflictDetectResp detectConflictRule(Long tenantId, ConflictRuleDetectReq req);

    /**
     * 批量删除冲突规则
     * <p>
     * 批量软删除多个冲突规则实体（幂等：不存在/已删的 id 静默跳过）。
     * 类型级 CONFLICT_RULE:DELETE 门禁，全有或全无（T-PERM-030 收窄）。
     * </p>
     *
     * @param tenantId   租户ID
     * @param ids        规则ID列表
     * @param operatorId 操作者ID
     * @throws SecurityException 无 CONFLICT_RULE:DELETE 类型级权限时抛出（整批不变更）
     */
    void deleteConflictRulesByIds(Long tenantId, List<Long> ids, Long operatorId);
}