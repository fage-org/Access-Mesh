package cn.ac.fage.accessmesh.gateway.config;

import io.netty.channel.ChannelOption;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * WebClient配置类
 * <p>
 * 提供负载均衡的WebClient.Builder，用于通过逻辑名称调用内部服务
 * （如lb://access-service）。
 * 配置连接超时和响应超时以防止长时间阻塞。
 * </p>
 */
@Configuration
public class WebClientConfig {

    /**
     * 创建负载均衡WebClient构建器
     * <p>
     * 配置Netty HttpClient：
     * - 连接超时: 3秒
     * - 响应超时: 5秒
     * 使用@LoadBalanced注解支持服务发现和负载均衡。
     * </p>
     *
     * @return 负载均衡WebClient构建器实例
     */
    @Bean
    @LoadBalanced
    public WebClient.Builder loadBalancedWebClientBuilder() {
        HttpClient httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000)
            .responseTimeout(Duration.ofSeconds(5));
        return WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient));
    }
}