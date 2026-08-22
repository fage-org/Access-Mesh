package cn.ac.fage.accessmesh.perm.client.config;

import cn.ac.fage.accessmesh.perm.client.feign.FeignInternalSyncInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 权限客户端自动配置类
 * <p>
 * 自动启用Feign客户端扫描，用于权限服务远程调用。
 * 当配置属性 perm.client.enabled 为 true 时启用（默认启用）。
 * </p>
 * <p>
 * T-ACCESS-010 评审 P1 修复：{@link EnableFeignClients} 只注册 Feign 接口，
 * 不会注册同包的普通 {@code @Component}，而业务服务的组件扫描覆盖不到本包——
 * 因此 {@link FeignInternalSyncInterceptor} 必须显式 {@code @Import} 装配，
 * 否则 SDK 调用不携带 {@code X-Internal-Secret}/{@code X-Service-Code} 会被
 * access-service 拒绝，且 {@code perm.service-code} 缺失 fail-fast 不会生效。
 * 拦截器自身的 {@code @ConditionalOnProperty("perm.internal-secret")} 在
 * {@code @Import} 导入时照常评估：未配置 internal-secret 的服务不注册。
 * </p>
 */
@Configuration
@EnableFeignClients(basePackages = "cn.ac.fage.accessmesh.perm.client.feign")
@Import(FeignInternalSyncInterceptor.class)
@ConditionalOnProperty(name = "perm.client.enabled", havingValue = "true", matchIfMissing = true)
public class PermClientAutoConfiguration {
}
