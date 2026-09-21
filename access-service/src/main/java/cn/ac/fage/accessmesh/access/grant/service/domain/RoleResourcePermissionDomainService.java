package cn.ac.fage.accessmesh.access.grant.service.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 角色资源授权事实领域服务（Q-009 收敛产物，T-ACCESS-044）。
 * <p>
 * 承接他能力包（type/resource/rule）对 {@code role_resource_permission} 的
 * 级联盘点读、引用守卫读与级联软删写——原冻结白名单 4 条入边的统一封装面。
 * 硬契约：仅依赖本包 mapper（不注入任何他能力包 bean，Spring 无环）；
 * 全部方法无缓存直读直写（保持同事务可见性——recycle 引用归零判定在调用方
 * 事务软删授权行之后执行，必须读到未提交状态）；不声明独立事务
 * （REQUIRED 跟随调用方，事务边界在 AppService）。
 * </p>
 */
public interface RoleResourcePermissionDomainService {

    /**
     * 查询指定资源 ID 集合的有效权限行 ID（删除级联盘点）。
     *
     * @param tenantId    租户ID
     * @param resourceIds 资源ID集合
     * @return 权限行ID列表
     */
    List<Long> selectValidPermIdsByResourceIds(Long tenantId, List<Long> resourceIds);

    /**
     * 查询指定资源类型下被有效 MANUAL/AUTO_DEP 授权行直接引用的操作位集合
     * （T-PERM-072 操作生命周期守卫；AUTHORITY_ROOT 基座行不算用户引用）。
     *
     * @param tenantId      租户ID
     * @param resourceType  资源类型内部值
     * @param operationBits 候选操作位集合
     * @return 被引用的操作位集合（候选中未被引用的不在结果中）
     */
    Set<Long> selectReferencedOperationBits(Long tenantId, Integer resourceType, Set<Long> operationBits);

    /**
     * 查询指定资源 ID 集合涉及的受影响角色 ID 集合（软删前登记 ROLE_PERM_SNAPSHOT 失效）。
     *
     * @param tenantId    租户ID
     * @param resourceIds 资源ID集合
     * @return 受影响角色ID集合
     */
    Set<Long> selectRoleIdsByResourceIds(Long tenantId, List<Long> resourceIds);

    /**
     * 查询指定资源类型值集合的有效权限行 ID（类型删除级联盘点；仅限 typeKey=resource_type 类型值）。
     *
     * @param tenantId     租户ID
     * @param resourceTypes 资源类型值集合
     * @return 权限行ID列表
     */
    List<Long> selectValidPermIdsByResourceTypes(Long tenantId, Set<Integer> resourceTypes);

    /**
     * 查询指定资源类型值集合的有效授权行受影响角色 ID 集合（类型删除级联登记失效）。
     *
     * @param tenantId     租户ID
     * @param resourceTypes 资源类型值集合
     * @return 受影响角色ID集合
     */
    Set<Long> selectRoleIdsByResourceTypes(Long tenantId, Set<Integer> resourceTypes);

    /**
     * 按权限行 ID 集合查询其非空 condition_id（级联删授权行前收集内联条件回收候选）。
     *
     * @param tenantId      租户ID
     * @param permissionIds 权限行ID集合
     * @return 非空 condition_id 去重集合
     */
    Set<Long> selectConditionIdsByPermIds(Long tenantId, List<Long> permissionIds);

    /**
     * 查询条件 ID 集合中仍被有效授权行引用的 condition_id（条件删除引用守卫 / 内联回收归零判定共用；
     * 无缓存直读——调用方事务内软删授权行后必须读到未提交状态）。
     *
     * @param tenantId     租户ID
     * @param conditionIds 条件ID集合
     * @return 仍被引用的 condition_id 集合
     */
    Set<Long> selectReferencedConditionIds(Long tenantId, Set<Long> conditionIds);

    /**
     * 根据条件 ID 集合反查受影响的服务编码集合（条件变更登记 serviceCodes 广播失效）。
     *
     * @param tenantId     租户ID
     * @param conditionIds 条件ID集合
     * @return 受影响的服务编码集合
     */
    Set<String> selectServiceCodesByConditionIds(Long tenantId, Set<Long> conditionIds);

    /**
     * 批量软删授权行（删除级联本体；同事务内先盘点登记失效再调用）。
     *
     * @param tenantId  租户ID
     * @param ids       待删除的权限行ID列表
     * @param deletedAt 删除时间戳
     * @return 更新的行数
     */
    int softDeleteBatch(Long tenantId, List<Long> ids, LocalDateTime deletedAt);
}
