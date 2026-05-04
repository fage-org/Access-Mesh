package cn.ac.fage.accessmesh.common.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

import cn.ac.fage.accessmesh.common.cache.CacheAutoConfiguration;

/**
 * Common 模块自动配置。
 * 自动加载全局异常处理器和其他公共组件。
 */
@AutoConfiguration
@ComponentScan(basePackages = {
    "cn.ac.fage.accessmesh.common.exception"
})
@Import(CacheAutoConfiguration.class)
public class CommonAutoConfiguration {
}
