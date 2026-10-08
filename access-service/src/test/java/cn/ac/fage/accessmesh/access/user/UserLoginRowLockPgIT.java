package cn.ac.fage.accessmesh.access.user;

import cn.ac.fage.accessmesh.access.it.ItInfra;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.user.entity.SysUser;
import cn.ac.fage.accessmesh.access.user.mapper.SysUserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 登录行锁串行化的数据库级证据（2026-10-06 复评轮拍板：行锁串行化）。
 * <p>
 * codex 复评发现：login「读用户→验密→签发」与 resetPassword「改密→吊销」无串行边界时，
 * 在途旧密码登录可在重置吊销后建立新会话。修复=login 事务内行锁读
 * （{@code selectByUsernameForUpdate}），与 resetPassword 的 UPDATE 行锁互斥。
 * 本类在真实 PG 上锁死该互斥语义：持有者事务模拟重置方（行锁读 + 同事务 UPDATE 新哈希）
 * 后挂起（闭锁控制，确定性编排），观察窗内并发的行锁读不得完成；释放提交后行锁读
 * 必须读到已提交的新哈希。
 * </p>
 * <p>
 * 2026-10-06 逐任务评审修正（多卡重复发现）：旧编排持有者只锁读不更新，另设独立 updater
 * 与 waiter 竞争释放后的锁授予——waiter 先获锁读 OLD_HASH 是合法串行序却被终态断言判负
 * （间歇假失败）。改为持有者即重置方（锁读+UPDATE），终态读与授予顺序解耦、恒为 NEW_HASH；
 * 红跑能力保留：去掉 FOR UPDATE 后观察窗断言（行锁读不得完成）必红。
 * 观察窗为有界轮询（非裸 sleep 时序余量）。
 * </p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Tag("testcontainers")
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
    "spring.config.import=optional:classpath:/test-nacos-dummy.yml",
    "spring.cloud.nacos.config.enabled=false",
    "spring.cloud.nacos.config.import-check.enabled=false",
    "spring.cloud.nacos.discovery.enabled=false",
    "accessmesh.sync.scheduler.enabled=false", "access.tenant.gate-repair.enabled=false",
    "mybatis-flex.configuration.map-underscore-to-camel-case=true",
    "logging.level.cn.ac.fage.accessmesh=WARN",
})
class UserLoginRowLockPgIT {

    private static final Long TENANT = 1L;
    private static final String USERNAME = "row-lock-user";
    private static final String OLD_HASH = "$2a$10$oldhasholdhasholdhasholdhasholdhashou";
    private static final String NEW_HASH = "$2a$10$newhashnewhashnewhashnewhashnewhasho";

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        ItInfra.register(registry, UserLoginRowLockPgIT.class);
    }

    @Autowired
    private SysUserMapper userMapper;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager txManager;

    @BeforeEach
    void seedUser() {
        jdbc.update("""
            INSERT INTO sys_user (id, tenant_id, username, password, name, status)
            VALUES (910001, ?, ?, ?, 'rowlock', 1)
            """, TENANT, USERNAME, OLD_HASH);
    }

    @AfterEach
    void cleanUser() {
        jdbc.update("DELETE FROM sys_user WHERE id = 910001");
    }

    @Test
    void lockedReadAndUpdateSerializeOnTheUserRow() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(3)) {

            Future<Integer> holder = pool.submit(() -> tx.execute(status -> {
                // 生成 SQL（EntitySqlProvider.update）要求租户上下文在岗——池线程手工绑定
                // （RequestContextInterceptor 请求线程同款语义），XML 锁读则带显式参数不受影响
                TenantContextHolder.setTenantId(TENANT);
                try {
                    SysUser locked = userMapper.selectByUsernameForUpdate(TENANT, USERNAME);
                    assertThat(locked).isNotNull();
                    SysUser patch = com.mybatisflex.core.util.UpdateEntity.of(SysUser.class);
                    patch.setId(910001L);
                    patch.setPassword(NEW_HASH);
                    int updated = userMapper.update(patch);
                    assertThat(updated).isEqualTo(1);
                    lockHeld.countDown();
                    try {
                        assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return updated;
                } finally {
                    TenantContextHolder.clear();
                }
            }));

            assertThat(lockHeld.await(10, TimeUnit.SECONDS)).as("持有者应取得行锁").isTrue();

            Future<SysUser> waiter = pool.submit(() ->
                tx.execute(status -> userMapper.selectByUsernameForUpdate(TENANT, USERNAME)));

            for (int i = 0; i < 15; i++) {
                assertThat(waiter.isDone()).as("持有者提交前行锁读不得完成").isFalse();
                Thread.sleep(20);
            }

            release.countDown();
            assertThat(holder.get(10, TimeUnit.SECONDS)).isEqualTo(1);

            SysUser after = waiter.get(10, TimeUnit.SECONDS);
            assertThat(after.getPassword())
                .as("重置方（锁读+UPDATE）提交后，行锁读必须读到已提交的新哈希"
                    + "（重置先提交→旧密码按新哈希校验失败）——与释放后的锁授予顺序无关")
                .isEqualTo(NEW_HASH);
        }
    }
}
