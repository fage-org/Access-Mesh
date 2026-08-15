package cn.ac.fage.accessmesh.access.permission.service.domain;

import cn.ac.fage.accessmesh.access.permission.entity.RoleResourcePermission;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 权限授予领域服务接口
 * <p>
 * 提供权限授予相关的核心领域逻辑：
 * - 授权传递检查（canGrant验证）
 * - 权限撤销
 *
 * TODO: 自动授权解析（resolveAutoGrants）——依赖资源的自动授权尚未实现
 * </p>
 */
public interface PermissionGrantDomainService {

    /**
     * 检查操作者是否可以授予指定权限给他人
     * <p>
     * 操作者必须满足以下条件：
     * 1. 拥有相同权限（资源类型+资源编码+编码类型/范围全部+操作）
     * 2. 该权限配置的canGrant=true
     * 用于权限授予流程中的委托验证。
     * 主体必须是权限域投影主体（{@code abstract_user.id}），禁止直接传 admin 域
     * {@code sys_user.id}（先经 {@code OperatorSubjectResolver.requireSubjectId} 转换）。
     * </p>
     *
     * @param tenantId         租户ID
     * @param subjectId        权限域投影主体ID（abstract_user.id）
     * @param resourceTypeCode 资源类型编码
     * @param resourceCode     资源编码（scopeAll=true时为null）
     * @param codeType         编码类型（scopeAll=true时为null）
     * @param operationCode    操作编码
     * @param scopeAll         是否范围全部
     * @param domainCode       业务域编码，可选
     * @return 是否有权限且canGrant=true
     */
    boolean canGrantPermission(Long tenantId, Long subjectId, String resourceTypeCode,
                               String resourceCode, String codeType, String operationCode,
                               boolean scopeAll, String domainCode);

    /**
     * 批量检查操作者是否可以授予多个权限（canGrant验证）
     * <p>
     * 返回每个权限键的详细检查结果。
     * 采用批量处理策略避免N+1查询：
     * 1. 批量解析资源类型值
     * 2. 批量查询操作权限
     * 3. 批量解析资源实体ID
     * 4. 批量查询角色资源权限
     * 5. 构建查找映射并逐个评估
     * 主体必须是权限域投影主体（{@code abstract_user.id}），禁止直接传 admin 域
     * {@code sys_user.id}（先经 {@code OperatorSubjectResolver.requireSubjectId} 转换）。
     * </p>
     *
     * @param tenantId    租户ID
     * @param subjectId   权限域投影主体ID（abstract_user.id）
     * @param permissions 待检查的权限集合
     * @param domainCode  业务域编码，可选
     * @return 权限键到检查结果的映射
     */
    Map<String, GrantCheckResult> checkCanGrant(Long tenantId, Long subjectId,
                                                 Set<GrantCheckKey> permissions, String domainCode);

    /**
     * 校验 MANUAL 直接授权的单记录不变量。
     * <p>
     * 同一租户、角色、资源/范围、操作位及父权限下最多一条有效 MANUAL 记录；
     * conditionId/canGrant 是可变属性，不参与身份。AUTO_DEP 与 MANUAL 可并存。
     * </p>
     *
     * @param existingPermissions 当前角色的有效权限
     * @param newPermissions      本次待新增权限
     * @param removedPermissionIds 本事务先移除的权限 ID
     */
    void validateSingleManualGrants(List<RoleResourcePermission> existingPermissions,
                                    List<RoleResourcePermission> newPermissions,
                                    Set<Long> removedPermissionIds);

    /**
     * 校验权限记录的最终可变属性。
     *
     * <p>条件权限不能继续转授：只要存在 conditionId，canGrant 必须为 false。</p>
     *
     * @param permission 待校验的最终权限记录
     */
    void validateGrantAttributes(RoleResourcePermission permission);

    /**
     * 批量撤销角色权限
     * <p>
     * 批量软删除权限，同时级联删除依赖该权限的子权限。
     * 注意：缓存失效和广播由调用方通过 PermissionChangeContext + @PermissionChange afterCommit 统一处理，
     * 避免与批量授权等复合操作重复登记。
     * </p>
     *
     * @param tenantId      租户ID
     * @param roleId        角色ID
     * @param permissionIds 待撤销的权限ID列表
     */
    void revokePermissions(Long tenantId, Long roleId, java.util.List<Long> permissionIds);

    /**
     * 授权检查键
     * <p>
     * 表示单次授权检查的参数组合。
     * </p>
     *
     * @param resourceTypeCode 资源类型编码
     * @param resourceCode     资源编码
     * @param codeType         编码类型
     * @param operationCode    操作编码
     * @param scopeAll         是否范围全部
     */
    record GrantCheckKey(
        String resourceTypeCode,
        String resourceCode,
        String codeType,
        String operationCode,
        boolean scopeAll
    ) {}

    /**
     * 授权检查结果
     * <p>
     * 表示单次授权检查的结果，包含是否可授予和拒绝原因。
     * </p>
     *
     * @param canGrant 是否可以授予
     * @param reason   拒绝原因，可授予时为null
     */
    record GrantCheckResult(
        boolean canGrant,
        String reason
    ) {}
}
