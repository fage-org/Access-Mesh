package cn.ac.fage.accessmesh.permission.service.domain;

import cn.ac.fage.accessmesh.permission.entity.PermissionCondition;
import cn.ac.fage.accessmesh.permission.entity.PermissionConflictRule;

import java.util.List;
import java.util.Set;

/**
 * 高级功能领域服务接口
 * <p>
 * 提供权限条件（PermissionCondition）和权限冲突规则（PermissionConflictRule）
 * 的基础数据访问操作。权限条件定义了权限生效的附加约束规则，
 * 权限冲突规则定义了哪些操作权限组合被视为冲突。
 * 该接口封装统一的领域层访问API，供上层服务调用。
 * </p>
 */
public interface AdvancedFeatureDomainService {

    // ===== 权限条件操作 =====

    /**
     * 根据ID和租户ID查询有效权限条件
     *
     * @param conditionId 条件ID
     * @param tenantId    租户ID
     * @return 权限条件实体，不存在返回null
     */
    PermissionCondition selectConditionById(Long conditionId, Long tenantId);

    /**
     * 根据租户ID查询所有有效权限条件列表
     *
     * @param tenantId 租户ID
     * @return 权限条件列表
     */
    List<PermissionCondition> selectConditionsByTenantId(Long tenantId);

    /**
     * 根据租户ID和条件编码集合批量查询有效权限条件
     *
     * @param tenantId 租户ID
     * @param codes    条件编码集合
     * @return 权限条件列表
     */
    List<PermissionCondition> selectConditionsByCodes(Long tenantId, Set<String> codes);

    /**
     * 根据租户ID和条件编码查询有效权限条件
     *
     * @param tenantId 租户ID
     * @param code     条件编码
     * @return 权限条件实体，不存在返回null
     */
    PermissionCondition selectConditionByCode(Long tenantId, String code);

    /**
     * 根据租户ID统计有效权限条件数量
     *
     * @param tenantId 租户ID
     * @return 匹配的权限条件数量
     */
    long countConditionsByTenantId(Long tenantId);

    /**
     * 插入权限条件
     *
     * @param entity 权限条件实体
     */
    void insertCondition(PermissionCondition entity);

    /**
     * 更新权限条件
     *
     * @param entity 权限条件实体
     * @return 更新影响的行数
     */
    int updateCondition(PermissionCondition entity);

    /**
     * 删除单个权限条件
     *
     * @param id       条件ID
     * @param tenantId 租户ID
     * @return 删除影响的行数
     */
    int deleteCondition(Long id, Long tenantId);

    /**
     * 批量删除权限条件
     *
     * @param ids      条件ID列表
     * @param tenantId 租户ID
     * @return 删除影响的行数
     */
    int deleteConditionsByIds(List<Long> ids, Long tenantId);

    // ===== 权限冲突规则操作 =====

    /**
     * 根据ID和租户ID查询有效权限冲突规则
     *
     * @param id       冲突规则ID
     * @param tenantId 租户ID
     * @return 权限冲突规则实体，不存在返回null
     */
    PermissionConflictRule selectConflictRuleById(Long id, Long tenantId);

    /**
     * 根据租户ID查询所有有效权限冲突规则列表
     *
     * @param tenantId 租户ID
     * @return 权限冲突规则列表
     */
    List<PermissionConflictRule> selectConflictRulesByTenantId(Long tenantId);

    /**
     * 根据租户ID和冲突类型查询有效权限冲突规则列表
     *
     * @param tenantId    租户ID
     * @param conflictType 冲突类型值
     * @return 权限冲突规则列表
     */
    List<PermissionConflictRule> selectConflictRulesByType(Long tenantId, Integer conflictType);

    /**
     * 根据租户ID统计有效权限冲突规则数量
     *
     * @param tenantId 租户ID
     * @return 匹配的权限冲突规则数量
     */
    long countConflictRulesByTenantId(Long tenantId);

    /**
     * 插入权限冲突规则
     *
     * @param entity 权限冲突规则实体
     */
    void insertConflictRule(PermissionConflictRule entity);

    /**
     * 更新权限冲突规则
     *
     * @param entity 权限冲突规则实体
     * @return 更新影响的行数
     */
    int updateConflictRule(PermissionConflictRule entity);

    /**
     * 删除单个权限冲突规则
     *
     * @param id       冲突规则ID
     * @param tenantId 租户ID
     * @return 删除影响的行数
     */
    int deleteConflictRule(Long id, Long tenantId);

    /**
     * 批量删除权限冲突规则
     *
     * @param ids      冲突规则ID列表
     * @param tenantId 租户ID
     * @return 删除影响的行数
     */
    int deleteConflictRulesByIds(List<Long> ids, Long tenantId);
}