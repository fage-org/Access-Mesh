package cn.ac.fage.accessmesh.permission.service;

import java.util.Map;
import java.util.Set;

/**
 * 授权检查服务接口
 * <p>
 * 提供专门的授权检查功能，主要用于委托授权验证。
 * 一般权限检查应直接使用PermQueryEngine：
 * <pre>
 * engine.hasPermission(tenantId, operatorId, ResourceTypeCode.ROLE, roleId, OperationCodeConstants.MANAGE);
 * engine.validate(tenantId, operatorId, ResourceTypeCode.RESOURCE, resourceId, OperationCodeConstants.MANAGE);
 * </pre>
 * 本服务仅提供canGrant权限检查，用于权限授予流程中的委托验证。
 * </p>
 */
public interface AuthorizationService {

    /**
     * 检查操作者是否可以授予指定权限给他人
     * <p>
     * 操作者必须满足以下条件：
     * 1. 拥有相同权限（资源类型+资源/范围全部+操作）
     * 2. 该权限配置的canGrant=true
     * 用于权限授予流程中的委托验证。
     * </p>
     *
     * @param tenantId         租户ID
     * @param operatorId       操作者用户ID
     * @param resourceTypeCode 资源类型编码
     * @param resourceCode     资源编码（scopeAll=true时为null）
     * @param operationCode    操作编码
     * @param scopeAll         是否范围全部
     * @param domainCode       业务域编码，可选
     * @return 是否有权限且canGrant=true
     */
    boolean canGrantPermission(Long tenantId, Long operatorId, String resourceTypeCode,
                               String resourceCode, String operationCode, boolean scopeAll, String domainCode);

    /**
     * 批量检查操作者是否可以授予多个权限
     * <p>
     * 返回每个权限键的详细检查结果。
     * </p>
     *
     * @param tenantId    租户ID
     * @param operatorId  操作者用户ID
     * @param permissions 待检查的权限集合
     * @param domainCode  业务域编码，可选
     * @return 权限键到检查结果的映射
     */
    Map<String, GrantCheckResult> checkGrantPermissionsBatch(Long tenantId, Long operatorId,
                                                              Set<GrantCheckKey> permissions, String domainCode);

    /**
     * 授权检查键
     * <p>
     * 表示单次授权检查的参数组合。
     * </p>
     *
     * @param resourceTypeCode 资源类型编码
     * @param resourceCode     资源编码
     * @param operationCode    操作编码
     * @param scopeAll         是否范围全部
     */
    record GrantCheckKey(
        String resourceTypeCode,
        String resourceCode,
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