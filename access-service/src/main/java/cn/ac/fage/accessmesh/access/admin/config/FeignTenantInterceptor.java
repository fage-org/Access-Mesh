package cn.ac.fage.accessmesh.access.admin.config;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.stereotype.Component;

/**
 * Feign租户拦截器
 * <p>
 * Feign请求拦截器，将租户ID传播到下游服务。
 * 确保所有调用permission-center的Feign请求都携带X-Tenant-Id请求头。
 * </p>
 */
@Component
public class FeignTenantInterceptor implements RequestInterceptor {

    private static final String HEADER_TENANT_ID = "X-Tenant-Id";

    /**
     * 应用拦截器逻辑
     * <p>
     * 从TenantContextHolder获取当前租户ID并添加到Feign请求头。
     * 如果租户ID存在，则添加X-Tenant-Id请求头。
     * </p>
     *
     * @param template Feign请求模板
     */
    @Override
    public void apply(RequestTemplate template) {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId != null) {
            template.header(HEADER_TENANT_ID, String.valueOf(tenantId));
        }
    }
}