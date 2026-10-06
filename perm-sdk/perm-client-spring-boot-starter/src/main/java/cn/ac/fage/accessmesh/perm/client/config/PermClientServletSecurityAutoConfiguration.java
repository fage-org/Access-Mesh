package cn.ac.fage.accessmesh.perm.client.config;

import cn.ac.fage.accessmesh.perm.client.security.GatewaySignatureFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/** Servlet 服务引入即获得身份头验签；非 Servlet 应用不注册过滤器。 */
@AutoConfiguration
@ConditionalOnClass(name = {"jakarta.servlet.Filter", "org.springframework.web.filter.OncePerRequestFilter"})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = "perm.client.enabled", havingValue = "true", matchIfMissing = true)
public class PermClientServletSecurityAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(GatewaySignatureFilter.class)
    public GatewaySignatureFilter gatewaySignatureFilter(
            @Value("${perm.client.signature.secret:${ACCESSMESH_SIGNATURE_SECRET:}}") String secret,
            @Value("${perm.client.signature.valid-seconds:300}") long validSeconds,
            ObjectMapper objectMapper) {
        return new GatewaySignatureFilter(secret, validSeconds, objectMapper);
    }
}
