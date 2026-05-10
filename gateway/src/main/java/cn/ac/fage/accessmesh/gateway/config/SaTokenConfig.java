package cn.ac.fage.accessmesh.gateway.config;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoRedisJackson;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sa-Token配置类
 * <p>
 * WebFlux网关的Sa-Token配置。
 * 不注册SaReactorFilter，认证由自定义过滤器链处理
 * （AuthTokenFilter + PermissionFilter），以维护正确的过滤器顺序。
 * </p>
 *
 * <p>注册Sa-Token的Redis DAO，使token查询通过Redis进行。
 * </p>
 */
@Configuration
public class SaTokenConfig {

    /**
     * 创建Sa-Token Redis DAO
     * <p>
     * 使用Jackson序列化的Redis DAO实现，
     * 支持token信息的Redis存储和查询。
     * </p>
     *
     * @return Sa-Token Redis DAO实例
     */
    @Bean
    public SaTokenDao saTokenDao() {
        return new SaTokenDaoRedisJackson();
    }
}