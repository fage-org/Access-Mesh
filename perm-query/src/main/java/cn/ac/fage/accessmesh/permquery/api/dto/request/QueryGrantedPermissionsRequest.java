package cn.ac.fage.accessmesh.permquery.api.dto.request;

import java.util.Set;

/**
 * 权限视图查询请求
 */
public class QueryGrantedPermissionsRequest {

    private Long tenantId;
    private Long userId;
    private Set<Long> roleIds;
    private Set<String> resourceTypeCodes;

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

    public Set<String> getResourceTypeCodes() {
        return resourceTypeCodes;
    }

    public void setResourceTypeCodes(Set<String> resourceTypeCodes) {
        this.resourceTypeCodes = resourceTypeCodes;
    }
}