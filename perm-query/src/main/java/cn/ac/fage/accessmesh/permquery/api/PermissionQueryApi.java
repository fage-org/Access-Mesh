package cn.ac.fage.accessmesh.permquery.api;

import cn.ac.fage.accessmesh.permquery.api.dto.request.BatchQueryPermissionRequest;
import cn.ac.fage.accessmesh.permquery.api.dto.request.FilterAccessibleResourcesRequest;
import cn.ac.fage.accessmesh.permquery.api.dto.request.QueryGrantedPermissionsRequest;
import cn.ac.fage.accessmesh.permquery.api.dto.request.QueryPermissionRequest;
import cn.ac.fage.accessmesh.permquery.api.dto.response.BatchPermissionQueryResponse;
import cn.ac.fage.accessmesh.permquery.api.dto.response.PermissionQueryResponse;
import cn.ac.fage.accessmesh.permquery.application.PermissionQueryService;
import cn.ac.fage.accessmesh.permquery.domain.model.valueobject.PermissionQuery;
import cn.ac.fage.accessmesh.permquery.domain.model.valueobject.PermissionQueryResult;
import cn.ac.fage.accessmesh.permquery.domain.model.valueobject.QueryOptions;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 权限查询API控制器
 * <p>
 * 提供权限查询的REST API接口。
 * 返回完整权限数据，调用方自行判断决策。
 * </p>
 */
@RestController
@RequestMapping("/api/v2/perm-query")
public class PermissionQueryApi {

    private final PermissionQueryService service;

    public PermissionQueryApi(PermissionQueryService service) {
        this.service = service;
    }

    /**
     * 权限查询（返回完整权限数据）
     */
    @PostMapping("/query")
    public PermissionQueryResponse queryPermission(@RequestBody QueryPermissionRequest request) {
        QueryOptions options = request.getOptions() != null ? request.getOptions() : QueryOptions.forCheck();

        PermissionQueryResult result;
        if (request.getRoleIds() != null && !request.getRoleIds().isEmpty()) {
            result = service.queryByRoles(
                request.getTenantId(),
                request.getRoleIds(),
                request.getResourceTypeCode(),
                request.getResourceCode(),
                request.getOperationCode(),
                options
            );
        } else {
            result = service.query(
                request.getTenantId(),
                request.getUserId(),
                request.getResourceTypeCode(),
                request.getResourceCode(),
                request.getOperationCode(),
                options
            );
        }

        return toResponse(result);
    }

    /**
     * 批量权限查询（返回每个资源的完整权限数据）
     */
    @PostMapping("/batch-query")
    public BatchPermissionQueryResponse batchQueryPermission(@RequestBody BatchQueryPermissionRequest request) {
        QueryOptions options = request.getOptions() != null ? request.getOptions() : QueryOptions.forCheck();

        Map<String, PermissionQueryResult> results;
        if (request.getRoleIds() != null && !request.getRoleIds().isEmpty()) {
            // TODO: 添加批量按角色查询的service方法
            PermissionQueryResult result = service.queryByRoles(
                request.getTenantId(),
                request.getRoleIds(),
                request.getResourceTypeCode(),
                request.getResourceCodes().iterator().next(),
                request.getOperationCode(),
                options
            );
            results = Map.of(request.getResourceCodes().iterator().next(), result);
        } else {
            results = service.batchQuery(
                request.getTenantId(),
                request.getUserId(),
                request.getResourceTypeCode(),
                request.getResourceCodes(),
                request.getOperationCode(),
                options
            );
        }

        return toBatchResponse(results);
    }

    /**
     * 权限视图查询
     */
    @PostMapping("/granted-permissions")
    public PermissionQueryResponse queryGrantedPermissions(@RequestBody QueryGrantedPermissionsRequest request) {
        PermissionQueryResult result = service.queryGrantedPermissions(
            request.getTenantId(),
            request.getUserId(),
            request.getRoleIds(),
            request.getResourceTypeCodes()
        );
        return toResponse(result);
    }

    /**
     * 可访问资源过滤
     */
    @PostMapping("/accessible-resources")
    public Set<Long> filterAccessibleResources(@RequestBody FilterAccessibleResourcesRequest request) {
        return service.filterAccessibleResources(
            request.getTenantId(),
            request.getUserId(),
            request.getResourceTypeCodes(),
            request.getOperationCodes()
        );
    }

    // ===== 响应转换方法 =====

    private PermissionQueryResponse toResponse(PermissionQueryResult result) {
        PermissionQueryResponse response = new PermissionQueryResponse();

        // 权限条目
        response.setScopeAllPermissions(result.scopeAllPermissions().stream()
            .map(PermissionQueryResponse.GrantedPermissionItem::from)
            .toList());
        response.setInstancePermissions(result.instancePermissions().stream()
            .map(PermissionQueryResponse.GrantedPermissionItem::from)
            .toList());

        // 辅助信息
        response.setRoles(result.roles().entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> PermissionQueryResponse.RoleInfo.from(e.getValue()))));
        response.setResources(result.resources().entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> PermissionQueryResponse.ResourceInfo.from(e.getValue()))));
        response.setOperations(result.operations().entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> PermissionQueryResponse.OperationInfo.from(e.getValue()))));
        response.setConditions(result.conditions().entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> PermissionQueryResponse.ConditionInfo.from(e.getValue()))));

        // 评估结果
        response.setConditionResults(result.conditionResults());
        response.setConflicts(result.conflicts().stream()
            .map(PermissionQueryResponse.ConflictInfoItem::from)
            .toList());

        // 查询上下文
        response.setResolvedRoleIds(result.resolvedRoleIds());
        response.setResolvedResourceTypes(result.resolvedResourceTypes());

        return response;
    }

    private BatchPermissionQueryResponse toBatchResponse(Map<String, PermissionQueryResult> results) {
        BatchPermissionQueryResponse response = new BatchPermissionQueryResponse();

        response.setResults(results.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> toResponse(e.getValue()))));

        // 公共信息可以从第一个结果中提取
        if (!results.isEmpty()) {
            PermissionQueryResult first = results.values().iterator().next();
            response.setRoles(first.roles().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> PermissionQueryResponse.RoleInfo.from(e.getValue()))));
            response.setOperations(first.operations().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> PermissionQueryResponse.OperationInfo.from(e.getValue()))));
        }

        return response;
    }
}