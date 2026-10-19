package cn.ac.fage.accessmesh.example.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import tools.jackson.databind.DeserializationFeature;

@Configuration
public class ExampleJsonConfiguration {
    /** Policy/filter numeric literals must reach DECIMAL columns without an intermediate double. */
    @Bean
    JsonMapperBuilderCustomizer exactDecimalValues() {
        return builder -> builder.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    }
}
