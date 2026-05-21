package cn.ac.fage.accessmesh.permquery.api.dto.request;

import java.util.Set;

/**
 * 可访问资源过滤请求
 */
public class FilterAccessibleResourcesRequest {

    private Long tenantId;
    private Long userId;
    private Set<String> resourceTypeCodes;
    private Set<String> operationCodes;

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

    public Set<String> getResourceTypeCodes() {
        return resourceTypeCodes;
    }

    public void setResourceTypeCodes(Set<String> resourceTypeCodes) {
        this.resourceTypeCodes = resourceTypeCodes;
    }

    public Set<String> getOperationCodes() {
        return operationCodes;
    }

    public void setOperationCodes(Set<String> operationCodes) {
        this.operationCodes = operationCodes;
    }
}