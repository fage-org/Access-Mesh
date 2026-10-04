package cn.ac.fage.accessmesh.perm.client.config;

import cn.ac.fage.accessmesh.perm.common.feign.FeignCredentialInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 权限查询与同步 Feign 客户端，显式装配服务凭证拦截器。
 * 业务服务无需配置平台内部共享密钥；管理面调用仍须独立用户身份。
 */
@Configuration
@EnableFeignClients(basePackages = "cn.ac.fage.accessmesh.perm.client.feign")
@Import(FeignCredentialInterceptor.class)
@ConditionalOnProperty(name = "perm.client.enabled", havingValue = "true", matchIfMissing = true)
public class PermClientAutoConfiguration {
}
