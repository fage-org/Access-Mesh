package cn.ac.fage.accessmesh.access.user;

import cn.ac.fage.accessmesh.access.it.ItInfra;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.user.entity.SysUser;
import cn.ac.fage.accessmesh.access.user.mapper.SysUserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
    @Autowired
    private org.redisson.api.RedissonClient redisson;
    @Autowired
    private cn.ac.fage.accessmesh.access.infrastructure.PasswordResetLockSupport passwordResetLocks;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void interruptedAcquisitionMustReleaseLateRedisSuccess(boolean treeLock) throws Exception {
        var replies = new DelayedReplies();
        var config = new org.redisson.config.Config(redisson.getConfig());
        config.setNettyHook(replies);
        config.useSingleServer().setConnectionMinimumIdleSize(1).setConnectionPoolSize(2)
            .setPingConnectionInterval(0);
        var ownerClient = org.redisson.Redisson.create(config);
        String key = treeLock
            ? cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport.lockKey(TENANT,
                cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport.TreeLockTarget.SYS_MENU)
            : cn.ac.fage.accessmesh.perm.common.util.BusinessKeyUtil.passwordResetLockKey(TENANT, 910120L);
        var probe = redisson.getLock(key);
        var owner = new java.util.concurrent.atomic.AtomicReference<Thread>();
        var failure = new java.util.concurrent.atomic.AtomicReference<RuntimeException>();
        var continued = new java.util.concurrent.atomic.AtomicBoolean();
        var interrupted = new java.util.concurrent.atomic.AtomicBoolean();
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            replies.pause();
            Future<?> request = pool.submit(() -> {
                owner.set(Thread.currentThread());
                try {
                    new TransactionTemplate(txManager).executeWithoutResult(status -> {
                        if (treeLock) {
                            new cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport(ownerClient)
                                .lockTreeWrites(TENANT,
                                    cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport.TreeLockTarget.SYS_MENU);
                        } else {
                            new cn.ac.fage.accessmesh.access.infrastructure.PasswordResetLockSupport(ownerClient)
                                .lockTenantUser(TENANT, 910120L);
                        }
                        continued.set(true);
                    });
                } catch (RuntimeException exception) {
                    failure.set(exception);
                    interrupted.set(Thread.currentThread().isInterrupted());
                } finally {
                    Thread.interrupted();
                }
            });
            assertThat(replies.arrived.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(probe.isLocked()).as("Redis 已取得锁，响应尚未交付调用线程").isTrue();
            owner.get().interrupt();
            replies.release();
            request.get(10, TimeUnit.SECONDS);
            assertThat(failure.get()).isNotNull();
            assertThat(interrupted.get()).isTrue();
            assertThat(continued.get()).as("中断后不得进入业务写入").isFalse();
            org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(3)).until(() -> {
                boolean acquired = probe.tryLock();
                if (acquired) probe.unlock();
                return acquired;
            });
        } finally {
            replies.release();
            pool.shutdownNow();
            pool.awaitTermination(10, TimeUnit.SECONDS);
            // 仅清本用例独占键，使旧实现反例失败后不会遗留续期锁污染后续验证。
            probe.forceUnlock();
            ownerClient.shutdown();
        }
    }

    /** 延迟真实 Redis 响应交付，不替换 RLock 或其取消/续期实现。 */
    private static final class DelayedReplies extends org.redisson.client.DefaultNettyHook {
        private final CountDownLatch arrived = new CountDownLatch(1);
        private final java.util.List<Runnable> held = new java.util.ArrayList<>();
        private boolean paused;

        synchronized void pause() { paused = true; }

        void release() {
            java.util.List<Runnable> pending;
            synchronized (this) {
                paused = false;
                pending = java.util.List.copyOf(held);
                held.clear();
            }
            pending.forEach(Runnable::run);
        }

        @Override
        public void afterChannelInitialization(io.netty.channel.Channel channel) {
            channel.pipeline().addFirst(new io.netty.channel.ChannelInboundHandlerAdapter() {
                @Override
                public void channelRead(io.netty.channel.ChannelHandlerContext context, Object message) {
                    synchronized (DelayedReplies.this) {
                        if (paused) {
                            held.add(() -> context.executor().execute(() -> {
                                context.fireChannelRead(message);
                                context.fireChannelReadComplete();
                            }));
                            arrived.countDown();
                            return;
                        }
                    }
                    context.fireChannelRead(message);
                }
            });
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void resetLockRejectsAnotherClientAndReleasesAfterCommitOrRollback(boolean rollback) throws Exception {
        var otherClient = org.redisson.Redisson.create(new org.redisson.config.Config(redisson.getConfig()));
        var other = new cn.ac.fage.accessmesh.access.infrastructure.PasswordResetLockSupport(otherClient);
        var transaction = new TransactionTemplate(txManager);
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> holder = pool.submit(() -> transaction.executeWithoutResult(status -> {
                passwordResetLocks.lockTenantUser(TENANT, 910001L);
                // 使用与生产写入相同的 Mapper/租户上下文，确保参与当前事务管理器。
                TenantContextHolder.setTenantId(TENANT);
                try {
                    SysUser patch = com.mybatisflex.core.util.UpdateEntity.of(SysUser.class);
                    patch.setId(910001L);
                    patch.setPassword(NEW_HASH);
                    assertThat(userMapper.update(patch)).isEqualTo(1);
                } finally {
                    TenantContextHolder.clear();
                }
                locked.countDown();
                try {
                    assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
                if (rollback) status.setRollbackOnly();
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> transaction.executeWithoutResult(status ->
                other.lockTenantUser(TENANT, 910001L)))
                .isInstanceOf(cn.ac.fage.accessmesh.common.exception.BizException.class)
                .extracting("errorCode").isEqualTo(10015);
            // 同号不同租户、另一用户与独立平台账号不互斥。
            transaction.executeWithoutResult(status -> {
                other.lockTenantUser(2L, 910001L);
                other.lockTenantUser(TENANT, 910002L);
                other.lockPlatformAccount(910001L);
            });
            release.countDown();
            holder.get(10, TimeUnit.SECONDS);
            transaction.executeWithoutResult(status -> other.lockTenantUser(TENANT, 910001L));
            assertThat(jdbc.queryForObject("SELECT password FROM sys_user WHERE tenant_id = ? AND id = 910001",
                String.class, TENANT)).isEqualTo(rollback ? OLD_HASH : NEW_HASH);
        } finally {
            release.countDown();
            pool.shutdownNow();
            pool.awaitTermination(10, TimeUnit.SECONDS);
            otherClient.shutdown();
        }
    }

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
