package cn.ac.fage.accessmesh.perm.data.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "perm.data.enabled", havingValue = "true", matchIfMissing = true)
public class PermDataAutoConfiguration {
}
