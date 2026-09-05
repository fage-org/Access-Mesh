package cn.ac.fage.accessmesh.access.permission.service.domain;

import cn.ac.fage.accessmesh.access.permission.entity.AbstractRole;
import cn.ac.fage.accessmesh.access.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.access.permission.entity.ResourceApiMapping;
import cn.ac.fage.accessmesh.access.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.access.permission.entity.RoleResourcePermission;
import cn.ac.fage.accessmesh.access.permission.entity.UserRole;

import java.util.List;
import java.util.Set;

/**
 * 空库 bootstrap 固定图专用领域组件（T-ACCESS-020；实现类包内可见，见 impl 包）。
 * <p>
 * <b>仅限 {@code access.application.bootstrap} 的幂等 initializer 使用，禁止业务调用方注入。</b>
 * 任务约束（access-service-architecture.md §14.2）：bootstrap 不走带操作者权限校验的管理
 * AppService、不给通用授权服务增加公开的无操作者入口——本组件即为"包内可见、bootstrap 专用
 * 的写入组件（复用 DomainService 内部逻辑）"：授权写入经
 * {@link PermissionGrantPlanDomainService#apply}（纯写入，复用既有 MANUAL 落库管线与
 * {@link PermissionGrantDomainService} 的不变量校验），资源/映射/绑定按同步链路同款唯一键
 * 语义直写。事务边界由调用方（initializer 单事务）声明。
 * </p>
 */
public interface BootstrapSeedWriter {

    /** 按 externalId 定位有效角色（跨角色类型——固定业务键被其他类型占用属冲突，需完整检出）。 */
    AbstractRole findRoleByExternalId(Long tenantId, String externalId);

    /** 按类型 + 业务编码批量定位有效资源（code_type 固定 default）。 */
    List<ResourceEntity> findResources(Long tenantId, Integer resourceTypeValue, Set<String> codes);

    /** 按资源 ID 集合定位有效 API 映射。 */
    List<ResourceApiMapping> findMappings(Long tenantId, Set<Long> resourceEntityIds);

    /** 角色当前有效授权全集（幂等状态②子集匹配检测用）。 */
    List<RoleResourcePermission> findValidGrants(Long tenantId, Long roleId);

    /**
     * 角色软删授权历史（含 {@code delete_flag != 0} 行，T-ACCESS-029 墓碑三分判定专用）。
     * <p>
     * <b>诊断例外：</b>仅 bootstrap 校验内部使用，禁止业务调用方将其外泄为通用
     * 「查历史软删」查询面。返回该角色软删历史全集，身份键（资源实体/范围 + 类型）匹配由
     * 调用方在内存完成——scopeAll 行 resource_entity_id 为 NULL，SQL 等值条件
     * {@code = NULL} 恒不命中，不能下推到 SQL。
     * </p>
     */
    List<RoleResourcePermission> findSoftDeletedGrants(Long tenantId, Long roleId);

    /** 主体与角色的有效绑定行。 */
    List<UserRole> findValidBindings(Long tenantId, Long subjectId, Long roleId);

    /** 批量查操作位定义（固定图授权 granted_bits 取自 operation_permission.binary_bit，禁止硬编码数值）。 */
    List<OperationPermission> findOperations(Long tenantId, Set<Integer> resourceTypeValues, Set<String> operationCodes);

    /**
     * 写 resource_entity（MANUAL 语义、owner=access-service、code_type=default、status=1）。
     *
     * @return resource_entity.id
     */
    Long insertResource(Long tenantId, Integer resourceTypeValue, String code, String name);

    /**
     * 写 resource_api_mapping（service_code=access-service、match_order=0、enabled=true）。
     */
    void insertApiMapping(Long tenantId, Long resourceEntityId, String httpMethod, String pathPattern);

    /**
     * 写 user_role 绑定（target_type=ROLE、relation_id=null，字段形态与管理链路 assignRole 一致）。
     */
    void insertRoleBinding(Long tenantId, Long subjectId, Long roleId);

    /**
     * 批量写入固定图授权（MANUAL 单操作位；经 PermissionGrantPlanDomainService.apply 落库，
     * 写前复用 validateSingleManualGrants / validateGrantAttributes 不变量校验）。
     */
    void insertGrants(Long tenantId, Long roleId, List<RoleResourcePermission> grants);
}
