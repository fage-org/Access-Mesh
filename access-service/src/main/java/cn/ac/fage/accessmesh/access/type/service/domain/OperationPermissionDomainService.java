package cn.ac.fage.accessmesh.access.type.service.domain;

import cn.ac.fage.accessmesh.access.type.entity.OperationPermission;

import java.util.List;
import java.util.Set;

/**
 * 操作定义事实领域服务（Q-009 收敛产物，T-ACCESS-045）。
 * <p>
 * 承接他能力包（grant/resource/rule/domain）对 {@code operation_permission} 的读取。
 * 硬契约：仅依赖本包 mapper（不注入任何他能力包 bean，Spring 无环）；全部方法
 * <b>无缓存直读</b>——刻意不接 {@code OPERATION_PERMISSIONS_BY_TYPE} 缓存：
 * 写校验面（PermissionGrantDomainServiceImpl 的 canGrant 刻意绕快照求新鲜防权限提升）
 * 与授权链路 knownOperationCodes 全租户口径（20008/20005 契约区分）都依赖活库读；
 * 不声明独立事务（REQUIRED 跟随调用方）。
 * </p>
 */
public interface OperationPermissionDomainService {

    /**
     * 查询租户内全部操作定义（= mapper {@code selectByTenantAndResourceType(tenantId, null)} 全租户口径）。
     * <p>
     * <b>口径红线</b>：消费方（grant App/Plan 的 knownOperationCodes）依赖「租户内全部操作码」
     * 区分 20008 RESOURCE_TYPE_OPERATION_MISMATCH（码存在于他类型）与 20005 OPERATION_NOT_FOUND
     * （T-PERM-040 契约区分锁定）——不得收窄为按请求类型过滤。
     * </p>
     *
     * @param tenantId 租户ID
     * @return 操作定义列表
     */
    List<OperationPermission> selectAllOperationsByTenant(Long tenantId);

    /**
     * 按资源类型值集合批量查询操作定义（IN 形态；空集短路返回空列表，禁产生 {@code IN ()}）。
     *
     * @param tenantId      租户ID
     * @param resourceTypes 资源类型值集合
     * @return 操作定义列表
     */
    List<OperationPermission> selectByTenantAndResourceTypes(Long tenantId, Set<Integer> resourceTypes);

    /**
     * 按资源类型值集合 + 操作码集合批量查询（写校验新鲜读面，双 IN）。
     *
     * @param tenantId         租户ID
     * @param resourceTypeValues 资源类型值集合
     * @param operationCodes   操作码集合（大写）
     * @return 操作定义列表
     */
    List<OperationPermission> selectByTenantResourceTypesAndOpCodes(Long tenantId,
                                                                    Set<Integer> resourceTypeValues,
                                                                    Set<String> operationCodes);

    /**
     * 按 ID 集合查有效操作定义。
     *
     * @param tenantId 租户ID
     * @param ids      操作定义ID集合
     * @return 操作定义列表
     */
    List<OperationPermission> selectValidByIds(Long tenantId, Set<Long> ids);
}
