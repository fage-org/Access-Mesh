package cn.ac.fage.accessmesh.perm.client.feign;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * 服务间内部调用拦截器
 * <p>
 * 当调用 access-service 的 {@code /api/perm/**} 路径时（典型为 sync/full-sync
 * 同步接口、auth/check 鉴权接口等），自动注入 {@code X-Internal-Secret}
 * 与 {@code X-Service-Code} 两个 Header，用于通过 access-service 端的
 * {@code InternalApiSecretInterceptor} 校验。
 * </p>
 * <p>
 * 仅当配置了 {@code perm.internal-secret} 时启用（由
 * {@code PermClientAutoConfiguration} 经 {@code @Import} 装配——业务服务的组件
 * 扫描覆盖不到 SDK 包，不得依赖 {@code @ComponentScan} 发现本类）；其他场景
 * （perm-sdk 被前端服务依赖时）不会因缺少配置启动失败。
 * </p>
 * <p>
 * {@code perm.service-code} 无默认值（T-ACCESS-010 用户决策）：配置了
 * {@code perm.internal-secret} 但未显式声明自身服务编码时，Spring 占位符解析
 * 失败导致启动失败（fail-fast），防止调用方以已退役的旧服务名声明身份。
 * </p>
 * <p>
 * {@code X-Tenant-Id} 由各调用方业务侧拦截器从 ThreadLocal 注入；本拦截器不处理。
 * </p>
 */
@Component
@ConditionalOnProperty(name = "perm.internal-secret")
public class FeignInternalSyncInterceptor implements RequestInterceptor {

    private static final String HEADER_INTERNAL_SECRET = "X-Internal-Secret";
    private static final String HEADER_SERVICE_CODE = "X-Service-Code";
    /** 内部调用统一路径前缀；仅匹配该前缀的请求才注入 secret，避免污染其他 Feign 调用。 */
    private static final String SYNC_PATH_MARKER = "/api/perm/";

    @Value("${perm.internal-secret:}")
    private String internalSecret;

    @Value("${perm.service-code}")
    private String serviceCode;

    @Override
    public void apply(RequestTemplate template) {
        String url = template.url();
        if (url == null || !url.contains(SYNC_PATH_MARKER)) {
            return;
        }
        if (internalSecret != null && !internalSecret.isBlank()) {
            template.header(HEADER_INTERNAL_SECRET, internalSecret);
        }
        // 若调用方未显式声明 X-Service-Code，则注入默认值；已显式声明则保留
        Collection<String> existing = template.headers().get(HEADER_SERVICE_CODE);
        if (existing == null || existing.isEmpty()) {
            template.header(HEADER_SERVICE_CODE, serviceCode);
        }
    }
}
