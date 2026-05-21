package cn.ac.fage.accessmesh.permquery.api.dto.request;

import cn.ac.fage.accessmesh.permquery.domain.model.valueobject.QueryOptions;

import java.util.Set;

/**
 * 批量权限查询请求
 */
public class BatchQueryPermissionRequest {

    private Long tenantId;
    private Long userId;
    private Set<Long> roleIds;
    private String resourceTypeCode;
    private Set<String> resourceCodes;
    private String operationCode;
    private QueryOptions options;

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Set<Long> getRoleIds() {
        return roleIds;
    }

    public void setRoleIds(Set<Long> roleIds) {
        this.roleIds = roleIds;
    }

    public String getResourceTypeCode() {
        return resourceTypeCode;
    }

    public void setResourceTypeCode(String resourceTypeCode) {
        this.resourceTypeCode = resourceTypeCode;
    }

    public Set<String> getResourceCodes() {
        return resourceCodes;
    }

    public void setResourceCodes(Set<String> resourceCodes) {
        this.resourceCodes = resourceCodes;
    }

    public String getOperationCode() {
        return operationCode;
    }

    public void setOperationCode(String operationCode) {
        this.operationCode = operationCode;
    }

    public QueryOptions getOptions() {
        return options;
    }

    public void setOptions(QueryOptions options) {
        this.options = options;
    }
}