package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.permission.service.domain.ResourcePermissionStrategy;
import org.springframework.stereotype.Component;

/**
 * Strategy for SERVICE resource ID conversion.
 * serviceCode IS the resource_entity.code directly.
 */
@Component
public class ServicePermissionStrategy implements ResourcePermissionStrategy<String> {

    private static final String SERVICE_TYPE_CODE = "SERVICE";

    @Override
    public String getResourceTypeCode() {
        return SERVICE_TYPE_CODE;
    }

    @Override
    public String toResourceEntityCode(Long tenantId, String serviceCode) {
        // serviceCode IS resource_entity.code
        return serviceCode;
    }
}