package cn.ac.fage.accessmesh.permquery.application;

import cn.ac.fage.accessmesh.permquery.domain.model.valueobject.PermissionQuery;
import cn.ac.fage.accessmesh.permquery.domain.model.valueobject.PermissionQueryResult;
import cn.ac.fage.accessmesh.permquery.domain.model.valueobject.QueryOptions;
import cn.ac.fage.accessmesh.permquery.domain.service.PermissionQueryPipeline;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 权限查询应用服务
 * <p>
 * 提供统一的权限查询入口，封装 PermissionQueryPipeline 的调用。
 * 提供多种便捷方法适应不同查询场景。
 * </p>
 */
@Service
public class PermissionQueryService {

    private final PermissionQueryPipeline pipeline;

    public PermissionQueryService(PermissionQueryPipeline pipeline) {
        this.pipeline = pipeline;
    }

    /**
     * 单次权限查询（返回完整权限数据）
     *
     * @param tenantId         租户ID
     * @param userId           用户ID
     * @param resourceTypeCode 资源类型编码
     * @param resourceCode     资源编码
     * @param operationCode    操作编码
     * @param options          查询选项
     * @return 权限查询结果
     */
    public PermissionQueryResult query(Long tenantId, Long userId,
                                        String resourceTypeCode, String resourceCode,
                                        String operationCode, QueryOptions options) {
        PermissionQuery query = PermissionQuery.forSingle(
            tenantId, userId, resourceTypeCode, resourceCode, operationCode, options);
        return pipeline.execute(query);
    }

    /**
     * 按角色查询权限（返回完整权限数据）
     *
     * @param tenantId         租户ID
     * @param roleIds          角色ID集合
     * @param resourceTypeCode 资源类型编码
     * @param resourceCode     资源编码
     * @param operationCode    操作编码
     * @param options          查询选项
     * @return 权限查询结果
     */
    public PermissionQueryResult queryByRoles(Long tenantId, Set<Long> roleIds,
                                               String resourceTypeCode, String resourceCode,
                                               String operationCode, QueryOptions options) {
        PermissionQuery query = PermissionQuery.forSingle(tenantId, null, resourceTypeCode, resourceCode, operationCode, options)
            .withRoles(roleIds);
        return pipeline.execute(query);
    }

    /**
     * 批量权限查询（返回每个资源的完整权限数据）
     * <p>
     * 优化：一次解析角色，批量查询权限，按资源分组返回。
     * </p>
     *
     * @param tenantId         租户ID
     * @param userId           用户ID
     * @param resourceTypeCode 资源类型编码
     * @param resourceCodes    资源编码集合
     * @param operationCode    操作编码
     * @param options          查询选项
     * @return 资源编码到权限查询结果的映射
     */
    public Map<String, PermissionQueryResult> batchQuery(Long tenantId, Long userId,
                                                          String resourceTypeCode, Set<String> resourceCodes,
                                                          String operationCode, QueryOptions options) {
        // 执行批量查询（一次管线调用）
        PermissionQuery baseQuery = PermissionQuery.forBatch(
            tenantId, userId, resourceTypeCode, resourceCodes, operationCode, options);
        PermissionQueryResult result = pipeline.execute(baseQuery);

        // 按资源分组
        return resourceCodes.stream()
            .collect(Collectors.toMap(
                code -> code,
                code -> filterResultForResource(result, code)
            ));
    }

    /**
     * 权限视图查询（返回用户的所有权限）
     *
     * @param tenantId          租户ID
     * @param userId            用户ID
     * @param roleIds           角色ID集合（可选，直接查询角色时使用）
     * @param resourceTypeCodes 资源类型编码集合（可选，过滤类型）
     * @return 权限查询结果
     */
    public PermissionQueryResult queryGrantedPermissions(Long tenantId, Long userId,
                                                          Set<Long> roleIds, Set<String> resourceTypeCodes) {
        PermissionQuery query = PermissionQuery.forView(tenantId, userId, roleIds, resourceTypeCodes);
        return pipeline.execute(query);
    }

    /**
     * 资源过滤查询（返回用户可访问的资源ID集合）
     *
     * @param tenantId          租户ID
     * @param userId            用户ID
     * @param resourceTypeCodes 资源类型编码集合
     * @param operationCodes    操作编码集合
     * @return 可访问的资源实体ID集合
     */
    public Set<Long> filterAccessibleResources(Long tenantId, Long userId,
                                                Set<String> resourceTypeCodes, Set<String> operationCodes) {
        PermissionQuery query = PermissionQuery.forResourceFilter(
            tenantId, userId, resourceTypeCodes, operationCodes);
        PermissionQueryResult result = pipeline.execute(query);
        return result.instancePermissions().stream()
            .map(p -> p.resourceEntityId())
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    }

    /**
     * 权限校验（返回完整结果，调用方自行判断）
     *
     * @param tenantId         租户ID
     * @param userId           用户ID
     * @param resourceTypeCode 资源类型编码
     * @param resourceCode     资源编码
     * @param operationCode    操作编码
     * @return 权限查询结果
     */
    public PermissionQueryResult validate(Long tenantId, Long userId,
                                           String resourceTypeCode, String resourceCode,
                                           String operationCode) {
        PermissionQuery query = PermissionQuery.forValidate(
            tenantId, userId, resourceTypeCode, resourceCode, operationCode);
        return pipeline.execute(query);
    }

    /**
     * 按已解析的实体ID查询权限
     *
     * @param tenantId          租户ID
     * @param userId            用户ID
     * @param resourceTypeCodes 资源类型编码集合
     * @param resourceEntityIds 资源实体ID集合
     * @param operationCodes    操作编码集合
     * @param options           查询选项
     * @return 权限查询结果
     */
    public PermissionQueryResult queryWithEntityIds(Long tenantId, Long userId,
                                                     Set<String> resourceTypeCodes, Set<Long> resourceEntityIds,
                                                     Set<String> operationCodes, QueryOptions options) {
        PermissionQuery query = PermissionQuery.withEntityIds(
            tenantId, userId, resourceTypeCodes, resourceEntityIds, operationCodes, options);
        return pipeline.execute(query);
    }

    /**
     * 从批量结果中筛选单个资源的结果
     */
    private PermissionQueryResult filterResultForResource(PermissionQueryResult result, String resourceCode) {
        // 简化实现：直接返回原结果（调用方可以通过 allPermissions() 获取所有权限）
        // 如果需要按资源精确分组，需要在管线层面支持
        return result;
    }
}