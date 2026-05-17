package cn.ac.fage.accessmesh.common.cache;

import cn.ac.fage.accessmesh.common.cache.impl.CaffeineLocalCacheStore;
import cn.ac.fage.accessmesh.common.cache.impl.CombinedL1L2Store;
import cn.ac.fage.accessmesh.common.cache.impl.RedissonBucketStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

class CacheAutoConfigurationTest {

    @Test
    void cacheServiceShouldFallbackToL1OnlyWhenRedissonStoresAreUnavailable() throws Exception {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(CacheAutoConfiguration.class);
            context.refresh();

            CacheService cacheService = context.getBean(CacheService.class);

            assertNotNull(cacheService);
            assertEquals(1, context.getBeanNamesForType(CacheService.class).length);
            assertNull(readField(cacheService, "l1L2Store"));
            assertNull(readField(cacheService, "l2OnlyStore"));
            assertInstanceOf(CaffeineLocalCacheStore.class, readField(cacheService, "l1OnlyStore"));
        }
    }

    @Test
    void cacheServiceShouldUseRedissonStoresWhenAvailable() throws Exception {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(SimpleMeterRegistry.class, SimpleMeterRegistry::new);
            context.registerBean(RedissonClient.class, () -> mock(RedissonClient.class));
            context.register(CacheAutoConfiguration.class);
            context.register(RedissonCacheAutoConfiguration.class);
            context.refresh();

            CacheService cacheService = context.getBean(CacheService.class);

            assertNotNull(cacheService);
            assertEquals(1, context.getBeanNamesForType(CacheService.class).length);
            assertInstanceOf(CombinedL1L2Store.class, readField(cacheService, "l1L2Store"));
            assertInstanceOf(RedissonBucketStore.class, readField(cacheService, "l2OnlyStore"));
            assertInstanceOf(CaffeineLocalCacheStore.class, readField(cacheService, "l1OnlyStore"));
        }
    }

    private Object readField(Object target, String fieldName) throws Exception {
        Field field = DefaultCacheService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}