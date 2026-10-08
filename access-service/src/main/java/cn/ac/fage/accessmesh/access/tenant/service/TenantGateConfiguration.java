package cn.ac.fage.accessmesh.access.tenant.service;

import cn.ac.fage.accessmesh.common.security.RedisTenantGateStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class TenantGateConfiguration {

    @Bean
    public RedisTenantGateStore tenantGateStore(StringRedisTemplate redis) {
        return new RedisTenantGateStore(redis);
    }
}
