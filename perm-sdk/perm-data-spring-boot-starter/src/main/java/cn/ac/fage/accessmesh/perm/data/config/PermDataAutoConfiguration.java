package cn.ac.fage.accessmesh.perm.data.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * 权限数据自动配置类
 * <p>
 * 当配置属性 perm.data.enabled 为 true 时启用（默认启用）。
 * 用于权限数据模块的自动装配。
 * </p>
 */
@Configuration
@ConditionalOnProperty(name = "perm.data.enabled", havingValue = "true", matchIfMissing = true)
public class PermDataAutoConfiguration {
}
