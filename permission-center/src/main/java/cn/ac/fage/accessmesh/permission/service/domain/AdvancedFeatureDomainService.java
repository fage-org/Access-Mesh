package cn.ac.fage.accessmesh.permission.service.domain;

import cn.ac.fage.accessmesh.permission.entity.PermissionCondition;
import cn.ac.fage.accessmesh.permission.entity.PermissionConflictRule;
import com.mybatisflex.core.query.QueryWrapper;

import java.util.List;

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
     * 根据ID查询权限条件
     *
     * @param id 权限条件ID
     * @return 权限条件实体，不存在返回null
     */
    PermissionCondition selectConditionById(Long id);

    /**
     * 根据查询条件查询权限条件列表
     *
     * @param qw QueryWrapper查询条件
     * @return 权限条件列表
     */
    List<PermissionCondition> selectConditionsByQuery(QueryWrapper qw);

    /**
     * 根据查询条件查询单个权限条件
     *
     * @param qw QueryWrapper查询条件
     * @return 权限条件实体，不存在返回null
     */
    PermissionCondition selectConditionByQuery(QueryWrapper qw);

    /**
     * 根据查询条件统计权限条件数量
     *
     * @param qw QueryWrapper查询条件
     * @return 匹配的权限条件数量
     */
    long countConditionsByQuery(QueryWrapper qw);

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
     * @param id 权限条件ID
     * @return 删除影响的行数
     */
    int deleteCondition(Long id);

    /**
     * 批量删除权限条件
     *
     * @param ids 权限条件ID列表
     * @return 删除影响的行数
     */
    int deleteConditionsByIds(List<Long> ids);

    // ===== 权限冲突规则操作 =====

    /**
     * 根据ID查询权限冲突规则
     *
     * @param id 权限冲突规则ID
     * @return 权限冲突规则实体，不存在返回null
     */
    PermissionConflictRule selectConflictRuleById(Long id);

    /**
     * 根据查询条件查询权限冲突规则列表
     *
     * @param qw QueryWrapper查询条件
     * @return 权限冲突规则列表
     */
    List<PermissionConflictRule> selectConflictRulesByQuery(QueryWrapper qw);

    /**
     * 根据查询条件查询单个权限冲突规则
     *
     * @param qw QueryWrapper查询条件
     * @return 权限冲突规则实体，不存在返回null
     */
    PermissionConflictRule selectConflictRuleByQuery(QueryWrapper qw);

    /**
     * 根据查询条件统计权限冲突规则数量
     *
     * @param qw QueryWrapper查询条件
     * @return 匹配的权限冲突规则数量
     */
    long countConflictRulesByQuery(QueryWrapper qw);

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
     * @param id 权限冲突规则ID
     * @return 删除影响的行数
     */
    int deleteConflictRule(Long id);

    /**
     * 批量删除权限冲突规则
     *
     * @param ids 权限冲突规则ID列表
     * @return 删除影响的行数
     */
    int deleteConflictRulesByIds(List<Long> ids);
}