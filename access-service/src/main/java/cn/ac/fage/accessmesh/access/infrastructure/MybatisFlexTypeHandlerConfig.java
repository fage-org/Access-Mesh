package cn.ac.fage.accessmesh.access.infrastructure;

import com.mybatisflex.core.mybatis.FlexConfiguration;
import com.mybatisflex.spring.boot.ConfigurationCustomizer;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

/**
 * MyBatis-Flex 全局 TypeHandler 注册（2026-08-22，用户决策全局 TypeHandler 方案）。
 * <p>
 * 将 {@link TimestamptzLocalDateTimeTypeHandler} 注册为 LocalDateTime 的默认处理器，
 * 覆盖内置的 getObject 实现——原因见该处理器 Javadoc（TIMESTAMPTZ 列 + pgjdbc 限制）。
 * </p>
 */
@Configuration
public class MybatisFlexTypeHandlerConfig implements ConfigurationCustomizer {

    @Override
    public void customize(FlexConfiguration configuration) {
        configuration.getTypeHandlerRegistry()
            .register(LocalDateTime.class, new TimestamptzLocalDateTimeTypeHandler());
    }
}
