package cn.ac.fage.accessmesh.common.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * Common模块自动配置类
 * <p>
 * 自动加载全局异常处理器和其他公共组件。
 * </p>
 *
 * <p>T-ACCESS-100（2026-10-14）：本类与缓存自动配置的注册均不在 common
 * （common 不携带任何 META-INF 注册，外部宿主引入权限 SDK 不被装配平台 WebMvc
 * 组件/缓存框架——缓存自动配置的方法签名引用 optional 依赖，宿主缺件时条件求值
 * 即 {@code NoClassDefFoundError}）；由平台应用各自的
 * AutoConfiguration.imports 声明：access-service/example-service 装配本类 +
 * 缓存两行；Gateway 将本类登记为候选但以主类 exclude 恒排除（exclude 校验要求
 * 被排除类是候选，且 E2E 共享大类路径会经兄弟服务注册文件带入本类——「注册以
 * 排除」对任意类路径形态合法且净效果稳定）。</p>
 *
 * <h3>缓存配置：</h3>
 * <p>
 * 缓存自动配置与本类同机制注册（各应用 AutoConfiguration.imports 自声明，T-ACCESS-100）：
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