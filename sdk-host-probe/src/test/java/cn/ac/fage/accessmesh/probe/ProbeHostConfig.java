package cn.ac.fage.accessmesh.probe;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Configuration;

/**
 * 宿主探针的空配置类：不带组件扫描，仅提供 SpringApplication 启动锚点 +
 * {@code @EnableAutoConfiguration}（真实宿主 {@code @SpringBootApplication}
 * 的装配语义——AutoConfiguration.imports 只经它触发，缺它则平台自动装配类
 * 的外溢无法被探针暴露）。上下文内容完全由「starter 传递闭包 + 自动装配」决定。
 */
@Configuration(proxyBeanMethods = false)
@EnableAutoConfiguration
public class ProbeHostConfig {
}
