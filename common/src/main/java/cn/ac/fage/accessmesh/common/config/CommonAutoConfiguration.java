package cn.ac.fage.accessmesh.common.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * Common模块自动配置类
 * <p>
 * 自动加载全局异常处理器和其他公共组件。
 * </p>
 *
 * <h3>缓存配置：</h3>
 * <p>
 * 缓存自动配置通过 Spring Boot AutoConfiguration.imports 直接注册：
 * <ul>
 *   <li>CacheAutoConfiguration - 基础配置，不依赖 Redisson</li>
 *   <li>RedissonCacheAutoConfiguration - 仅当 RedissonClient 类存在时加载</li>
 * </ul>
 * Gateway 等无 Redisson 依赖的模块可正常启动。
 * </p>
 */
@AutoConfiguration
@ComponentScan(basePackages = {
    "cn.ac.fage.accessmesh.common.exception",
    "cn.ac.fage.accessmesh.common.config"
})
public class CommonAutoConfiguration {
}