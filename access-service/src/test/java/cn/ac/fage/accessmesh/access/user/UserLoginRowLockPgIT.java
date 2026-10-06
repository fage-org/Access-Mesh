package cn.ac.fage.accessmesh.access.user;

import cn.ac.fage.accessmesh.access.it.ItInfra;
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
 * 本类在真实 PG 上锁死该互斥语义：持有者事务取行锁后挂起（闭锁控制，确定性编排），
 * 观察窗内并发的行锁读与 UPDATE 均不得完成；释放后按序完成，行锁读必须读到已提交的新哈希。
 * </p>
 * <p>
 * 观察窗为有界轮询（非裸 sleep 时序余量）：语义主体由「释放后完成序 + 读后值」承载，
 * 观察窗仅证明阻塞确实发生。
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
    "accessmesh.sync.scheduler.enabled=false",
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

            Future<SysUser> holder = pool.submit(() -> tx.execute(status -> {
                SysUser locked = userMapper.selectByUsernameForUpdate(TENANT, USERNAME);
                assertThat(locked).isNotNull();
                lockHeld.countDown();
                try {
                    assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return locked;
            }));

            assertThat(lockHeld.await(10, TimeUnit.SECONDS)).as("持有者应取得行锁").isTrue();

            Future<SysUser> waiter = pool.submit(() ->
                tx.execute(status -> userMapper.selectByUsernameForUpdate(TENANT, USERNAME)));
            Future<Integer> updater = pool.submit(() ->
                tx.execute(status -> jdbc.update(
                    "UPDATE sys_user SET password = ? WHERE id = 910001", NEW_HASH)));

            for (int i = 0; i < 15; i++) {
                assertThat(waiter.isDone()).as("持有者提交前行锁读不得完成").isFalse();
                assertThat(updater.isDone()).as("持有者提交前 UPDATE 不得完成").isFalse();
                Thread.sleep(20);
            }

            release.countDown();
            assertThat(holder.get(10, TimeUnit.SECONDS)).isNotNull();

            SysUser after = waiter.get(10, TimeUnit.SECONDS);
            assertThat(after.getPassword())
                .as("阻塞的行锁读在持有者提交后必须读到新哈希（重置先提交→旧密码按新哈希校验失败）")
                .isEqualTo(NEW_HASH);
            assertThat(updater.get(10, TimeUnit.SECONDS)).isEqualTo(1);
        }
    }
}
