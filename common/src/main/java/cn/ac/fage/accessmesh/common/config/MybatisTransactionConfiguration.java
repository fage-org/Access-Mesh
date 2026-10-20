package cn.ac.fage.accessmesh.common.config;

import cn.ac.fage.accessmesh.common.mybatis.SpringTransactionIsolationInterceptor;
import org.apache.ibatis.plugin.Interceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Platform Mapper integration; SDK-only hosts do not import CommonAutoConfiguration. */
@Configuration(proxyBeanMethods=false)
@ConditionalOnClass(Interceptor.class)
public class MybatisTransactionConfiguration {
    @Bean
    SpringTransactionIsolationInterceptor springTransactionIsolationInterceptor() {
        return new SpringTransactionIsolationInterceptor();
    }
}
