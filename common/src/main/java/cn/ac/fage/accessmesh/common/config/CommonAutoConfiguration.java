package cn.ac.fage.accessmesh.common.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

import cn.ac.fage.accessmesh.common.cache.CacheAutoConfiguration;

/**
 * Common模块自动配置类
 * <p>
 * 自动加载全局异常处理器和其他公共组件。
 * 包括：
 * <ul>
 *   <li>全局异常处理器（GlobalExceptionHandler）</li>
 *   <li>缓存自动配置（CacheAutoConfiguration）</li>
 * </ul>
 * </p>
 */
@AutoConfiguration
@ComponentScan(basePackages = {
    "cn.ac.fage.accessmesh.common.exception",
    "cn.ac.fage.accessmesh.common.config"
})
@Import(CacheAutoConfiguration.class)
public class CommonAutoConfiguration {
}