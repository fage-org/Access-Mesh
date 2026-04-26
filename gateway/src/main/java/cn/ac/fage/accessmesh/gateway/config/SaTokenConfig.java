package cn.ac.fage.accessmesh.gateway.config;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoRedisJackson;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sa-Token configuration for WebFlux gateway.
 *
 * No SaReactorFilter is registered here — auth is handled by the custom
 * filter chain (AuthTokenFilter + PermissionFilter) to maintain correct
 * ordering with header cleaning and whitelist matching.
 *
 * Sa-Token's Redis DAO is registered so that token lookups go through Redis.
 */
@Configuration
public class SaTokenConfig {

    @Bean
    public SaTokenDao saTokenDao() {
        return new SaTokenDaoRedisJackson();
    }
}
