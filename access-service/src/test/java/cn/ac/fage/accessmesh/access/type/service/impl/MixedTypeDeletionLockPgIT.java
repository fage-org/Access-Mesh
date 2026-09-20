package cn.ac.fage.accessmesh.access.type.service.impl;

import cn.ac.fage.accessmesh.access.engine.core.PermQueryEngine;
import cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport;
import cn.ac.fage.accessmesh.access.it.ItInfra;
import cn.ac.fage.accessmesh.access.type.service.TypeDefinitionAppService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/** 混合类型删除与现役组织/角色投影锁顺序的确定性交错，真实事务及 Redis 锁。 */
@Tag("testcontainers")
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "spring.config.import=optional:classpath:/test-nacos-dummy.yml",
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.nacos.config.import-check.enabled=false",
        "spring.cloud.nacos.discovery.enabled=false"
})
class MixedTypeDeletionLockPgIT {
    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        ItInfra.register(registry, MixedTypeDeletionLockPgIT.class);
    }

    @Autowired private TypeDefinitionAppService service;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;
    @SpyBean private TreeWriteLockSupport locks;
    @MockBean private PermQueryEngine gate;

    @Test
    void shouldAcquireRoleBeforeResource_whenRoleProjectionTransactionIsInFlight() throws Exception {
        when(gate.getDeniedResourceCodes(anyLong(), anyLong(), any(), any(), any())).thenReturn(Set.of());
        long resourceType = insertType("resource_type", "LOCK_RESOURCE", 920);
        long roleType = insertType("role_type", "LOCK_ROLE", 921);
        CountDownLatch roleHeld = new CountDownLatch(1);
        CountDownLatch deletionAtFirstLock = new CountDownLatch(1);
        CountDownLatch continueProjection = new CountDownLatch(1);
        AtomicBoolean takeResource = new AtomicBoolean(false);
        AtomicReference<TreeWriteLockSupport.TreeLockTarget> firstDeletionLock = new AtomicReference<>();
        doAnswer(invocation -> {
            if (Thread.currentThread().getName().equals("mixed-type-deletion")) {
                firstDeletionLock.compareAndSet(null, invocation.getArgument(1));
                deletionAtFirstLock.countDown();
            }
            return invocation.callRealMethod();
        }).when(locks).lockTreeWrites(anyLong(), any());

        try (var pool = Executors.newFixedThreadPool(2)) {
            var projection = pool.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                locks.lockTreeWrites(1L, TreeWriteLockSupport.TreeLockTarget.ABSTRACT_ROLE);
                roleHeld.countDown();
                await(continueProjection);
                // 镜像 OrgWriteAppServiceImpl 的既有先角色、后资源顺序。
                if (takeResource.get()) {
                    locks.lockTreeWrites(1L, TreeWriteLockSupport.TreeLockTarget.RESOURCE_ENTITY);
                }
                return true;
            }));
            try {
                await(roleHeld);
                var deletion = pool.submit(() -> {
                    Thread.currentThread().setName("mixed-type-deletion");
                    service.deleteTypesByIds(1L, List.of(resourceType, roleType), 100L);
                });
                await(deletionAtFirstLock);
                // 旧反序在此稳定失败；finally 释放角色事务，避免故意制造不可退出的真实死锁。
                assertThat(firstDeletionLock.get()).isEqualTo(TreeWriteLockSupport.TreeLockTarget.ABSTRACT_ROLE);
                takeResource.set(true);
                continueProjection.countDown();
                assertThat(projection.get(20, TimeUnit.SECONDS)).isTrue();
                deletion.get(20, TimeUnit.SECONDS);
                assertThat(jdbc.queryForObject("SELECT count(*) FROM type_definition WHERE id IN (?,?) AND delete_flag<>0",
                        Integer.class, resourceType, roleType)).isEqualTo(2);
            } finally {
                continueProjection.countDown();
            }
        }
    }

    private long insertType(String key, String code, int value) {
        return jdbc.queryForObject("INSERT INTO type_definition(tenant_id,type_key,type_code,type_value,name) VALUES (1,?,?,?,?) RETURNING id",
                Long.class, key, code, value, code);
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(20, TimeUnit.SECONDS)).as("lock entry gate reached").isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("lock gate interrupted", e);
        }
    }
}
