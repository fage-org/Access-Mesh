package cn.ac.fage.accessmesh.perm.client.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

/**
 * 权限查询与同步 Feign 客户端装配入口。
 * 业务服务无需配置平台内部共享密钥；管理面调用仍须独立用户身份。
 * 凭证拦截器不在此注册（T-ACCESS-098）：经 PermissionFeignClient 的
 * {@code @FeignClient(configuration=...)} 挂载到权限客户端自身 Feign 子上下文，
 * 不扩大为宿主全部 Feign 客户端的全局拦截器。
 */
@Configuration
@EnableFeignClients(basePackages = "cn.ac.fage.accessmesh.perm.client.feign")
@ConditionalOnProperty(name = "perm.client.enabled", havingValue = "true", matchIfMissing = true)
public class PermClientAutoConfiguration {
}
