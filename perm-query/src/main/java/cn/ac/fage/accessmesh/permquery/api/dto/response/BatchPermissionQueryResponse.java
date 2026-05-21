package cn.ac.fage.accessmesh.permquery.api.dto.response;

import java.util.Map;
import java.util.Set;

/**
 * 批量权限查询响应
 */
public class BatchPermissionQueryResponse {

    // 每个资源的权限查询结果
    private Map<String, PermissionQueryResponse> results;

    // 公共信息（避免重复）
    private Map<Long, PermissionQueryResponse.RoleInfo> roles;
    private Map<Long, PermissionQueryResponse.OperationInfo> operations;

    public Map<String, PermissionQueryResponse> getResults() {
        return results;
    }

    public void setResults(Map<String, PermissionQueryResponse> results) {
        this.results = results;
    }

    public Map<Long, PermissionQueryResponse.RoleInfo> getRoles() {
        return roles;
    }

    public void setRoles(Map<Long, PermissionQueryResponse.RoleInfo> roles) {
        this.roles = roles;
    }

    public Map<Long, PermissionQueryResponse.OperationInfo> getOperations() {
        return operations;
    }

    public void setOperations(Map<Long, PermissionQueryResponse.OperationInfo> operations) {
        this.operations = operations;
    }
}