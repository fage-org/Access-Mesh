package cn.ac.fage.accessmesh.gateway.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.config.WebFluxConfigurer;

/**
 * WebFlux配置类
 * <p>
 * 配置WebFlux的编解码器限制和自定义转换器。
 * 用于处理大请求体和响应体的编解码。
 * </p>
 */
@Configuration
public class WebFluxConfig implements WebFluxConfigurer {

    /**
     * 配置HTTP消息编解码器
     * <p>
     * 设置编解码器的内存限制。默认最大内存大小为256KB，
     * 增加到10MB以支持大请求体（如批量操作、文件上传等）。
     * </p>
     *
     * @param configurer 编解码器配置器
     */
    @Override
    public void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {
        // 默认最大内存大小为256KB，增加到10MB以支持大请求体
        configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024); // 10MB
    }
}