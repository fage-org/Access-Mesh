package cn.ac.fage.accessmesh.admin.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.stereotype.Component;

/**
 * Feign request interceptor that propagates tenant ID to downstream services.
 * Ensures X-Tenant-Id header is present for all Feign calls to permission-center.
 */
@Component
public class FeignTenantInterceptor implements RequestInterceptor {

    private static final String HEADER_TENANT_ID = "X-Tenant-Id";

    @Override
    public void apply(RequestTemplate template) {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId != null) {
            template.header(HEADER_TENANT_ID, String.valueOf(tenantId));
        }
    }
}