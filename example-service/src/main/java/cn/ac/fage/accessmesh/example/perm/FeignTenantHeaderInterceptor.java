package cn.ac.fage.accessmesh.example.perm;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.stereotype.Component;

/**
 * access-service 调用的 X-Tenant-Id 注入拦截器（T-ACCESS-061）。
 * <p>
 * SDK 的 {@code FeignInternalSyncInterceptor} 只注入密钥与服务身份，租户头按其
 * Javadoc 约定「由各调用方业务侧拦截器从 ThreadLocal 注入」。本拦截器从
 * {@link PermCallContext} 取 {@link BusinessPermChecker} 绑定的可信租户值注入，
 * 仅对 {@code /api/access/} 路径生效，不污染其他 Feign 调用。
 * </p>
 */
@Component
public class FeignTenantHeaderInterceptor implements RequestInterceptor {

    private static final String TENANT_HEADER = "X-Tenant-Id";
    private static final String ACCESS_PATH_MARKER = "/api/access/";

    @Override
    public void apply(RequestTemplate template) {
        String url = template.url();
        if (url == null || !url.contains(ACCESS_PATH_MARKER)) {
            return;
        }
        String tenantId = PermCallContext.getTenantId();
        if (tenantId != null && !tenantId.isBlank()) {
            template.header(TENANT_HEADER, tenantId);
        }
    }
}
