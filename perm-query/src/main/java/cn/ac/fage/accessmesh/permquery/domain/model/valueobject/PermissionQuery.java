package cn.ac.fage.accessmesh.permquery.domain.model.valueobject;

import java.util.Set;

/**
 * 权限查询输入值对象
 * <p>
 * 统一的权限查询输入，通过工厂方法创建不同场景的查询参数。
 * 不包含决策逻辑，只定义查询范围和选项。
 * </p>
 */
public record PermissionQuery(
    Long tenantId,
    Long userId,
    Set<Long> roleIds,
    Set<String> resourceTypeCodes,
    Set<String> resourceCodes,
    Set<Long> resourceEntityIds,
    Set<String> operationCodes,
    QueryOptions options
) {

    /**
     * 单次权限查询（指定资源类型、资源编码、操作编码）
     */
    public static PermissionQuery forSingle(Long tenantId, Long userId,
                                             String resourceTypeCode, String resourceCode, String operationCode,
                                             QueryOptions options) {
        return new PermissionQuery(
            tenantId, userId,
            null,
            resourceTypeCode != null ? Set.of(resourceTypeCode) : Set.of(),
            resourceCode != null ? Set.of(resourceCode) : null,
            null,
            operationCode != null ? Set.of(operationCode) : Set.of(),
            options != null ? options : QueryOptions.forCheck()
        );
    }

    /**
     * 批量权限查询（指定资源类型、多个资源编码、操作编码）
     */
    public static PermissionQuery forBatch(Long tenantId, Long userId,
                                            String resourceTypeCode, Set<String> resourceCodes, String operationCode,
                                            QueryOptions options) {
        return new PermissionQuery(
            tenantId, userId,
            null,
            resourceTypeCode != null ? Set.of(resourceTypeCode) : Set.of(),
            resourceCodes,
            null,
            operationCode != null ? Set.of(operationCode) : Set.of(),
            options != null ? options : QueryOptions.forCheck()
        );
    }

    /**
     * 权限视图查询（指定角色、资源类型范围）
     */
    public static PermissionQuery forView(Long tenantId, Long userId,
                                           Set<Long> roleIds, Set<String> resourceTypeCodes) {
        return new PermissionQuery(
            tenantId, userId,
            roleIds,
            resourceTypeCodes != null ? resourceTypeCodes : Set.of(),
            null,
            null,
            null,
            QueryOptions.forView()
        );
    }

    /**
     * 资源过滤查询（指定资源类型范围、操作范围）
     */
    public static PermissionQuery forResourceFilter(Long tenantId, Long userId,
                                                     Set<String> resourceTypeCodes, Set<String> operationCodes) {
        return new PermissionQuery(
            tenantId, userId,
            null,
            resourceTypeCodes != null ? resourceTypeCodes : Set.of(),
            null,
            null,
            operationCodes != null ? operationCodes : Set.of(),
            QueryOptions.forFilter()
        );
    }

    /**
     * 快速校验查询（类型级优先，无评估）
     */
    public static PermissionQuery forValidate(Long tenantId, Long operatorId,
                                               String resourceTypeCode, String resourceCode, String operationCode) {
        return new PermissionQuery(
            tenantId, operatorId,
            null,
            resourceTypeCode != null ? Set.of(resourceTypeCode) : Set.of(),
            resourceCode != null ? Set.of(resourceCode) : null,
            null,
            operationCode != null ? Set.of(operationCode) : Set.of(),
            QueryOptions.forValidate()
        );
    }

    /**
     * 直接使用角色ID查询（跳过用户角色解析）
     */
    public static PermissionQuery withRoles(Long tenantId, Set<Long> roleIds,
                                             Set<String> resourceTypeCodes, Set<String> operationCodes,
                                             QueryOptions options) {
        return new PermissionQuery(
            tenantId, null,
            roleIds,
            resourceTypeCodes != null ? resourceTypeCodes : Set.of(),
            null,
            null,
            operationCodes != null ? operationCodes : Set.of(),
            options != null ? options : QueryOptions.minimal()
        );
    }

    /**
     * 使用资源实体ID直接查询（跳过编码解析）
     */
    public static PermissionQuery withEntityIds(Long tenantId, Long userId,
                                                 Set<Long> roleIds, Set<Long> resourceEntityIds,
                                                 Set<String> operationCodes, QueryOptions options) {
        return new PermissionQuery(
            tenantId, userId,
            roleIds,
            null,
            null,
            resourceEntityIds,
            operationCodes != null ? operationCodes : Set.of(),
            options != null ? options : QueryOptions.minimal()
        );
    }

    // ===== 便捷方法 =====

    /**
     * 是否指定了角色ID（跳过用户角色解析）
     */
    public boolean hasRoleIds() {
        return roleIds != null && !roleIds.isEmpty();
    }

    /**
     * 是否指定了资源编码
     */
    public boolean hasResourceCodes() {
        return resourceCodes != null && !resourceCodes.isEmpty();
    }

    /**
     * 是否指定了资源实体ID
     */
    public boolean hasResourceEntityIds() {
        return resourceEntityIds != null && !resourceEntityIds.isEmpty();
    }

    /**
     * 是否指定了操作编码
     */
    public boolean hasOperationCodes() {
        return operationCodes != null && !operationCodes.isEmpty();
    }
}