package cn.ac.fage.accessmesh.access.permission.service;

import cn.ac.fage.accessmesh.access.permission.dto.req.ConflictRuleDetectReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.ConflictRuleReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.ConflictRuleUpdateReq;
import cn.ac.fage.accessmesh.access.permission.dto.resp.ConflictDetectResp;
import cn.ac.fage.accessmesh.access.permission.dto.resp.ConflictRuleResp;

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
     */
    ConflictRuleResp getConflictRule(Long tenantId, Long ruleId);

    /**
     * 查询冲突规则列表
     * <p>
     * 获取租户的所有冲突规则列表。
     * </p>
     *
     * @param tenantId 租户ID
     * @return 规则列表
     */
    List<ConflictRuleResp> listConflictRules(Long tenantId);

    /**
     * 更新冲突规则
     * <p>
     * 更新冲突规则的基本信息和检测条件。
     * </p>
     *
     * @param tenantId   租户ID
     * @param req        规则更新请求
     * @param operatorId 操作者ID
     * @return 更新后的规则详情
     */
    ConflictRuleResp updateConflictRule(Long tenantId, ConflictRuleUpdateReq req, Long operatorId);

    /**
     * 检测权限冲突
     * <p>
     * 根据冲突规则检测指定场景是否存在权限冲突。
     * 返回检测到的冲突详情。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      冲突检测请求，指定检测范围和条件
     * @return 冲突检测响应
     */
    ConflictDetectResp detectConflictRule(Long tenantId, ConflictRuleDetectReq req);

    /**
     * 删除冲突规则
     * <p>
     * 删除指定的冲突规则实体。
     * </p>
     *
     * @param tenantId   租户ID
     * @param ruleId     规则ID
     * @param operatorId 操作者ID
     */
    void deleteConflictRule(Long tenantId, Long ruleId, Long operatorId);

    /**
     * 批量删除冲突规则
     * <p>
     * 批量删除多个冲突规则实体。
     * </p>
     *
     * @param tenantId   租户ID
     * @param ids        规则ID列表
     * @param operatorId 操作者ID
     */
    void deleteConflictRulesByIds(Long tenantId, List<Long> ids, Long operatorId);
}