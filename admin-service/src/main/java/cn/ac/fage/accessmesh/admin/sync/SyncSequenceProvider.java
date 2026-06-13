package cn.ac.fage.accessmesh.admin.sync;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 同步任务序号生成器
 * <p>
 * 负责为 {@code SyncTaskEnvelope.syncSequenceNo} 生成单调递增序号。
 * 与 {@code syncOccurredAt} 共同构成 {@code syncVersion}，参与
 * permission-center 的乱序判定。
 * </p>
 * <p>
 * S4 阶段不接 redis，仅本地单调；多实例间通过初始值带入毫秒时钟与
 * 8 位随机扰动实现近似单调，确保不同实例的序号空间不会大量重叠。
 * 跨实例严格单调由 permission-center 端 {@code syncOccurredAt + syncSequenceNo}
 * 共同比较保证。
 * </p>
 */
@Component
public class SyncSequenceProvider {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AtomicLong counter;

    /**
     * 构造序号生成器
     * <p>
     * 初始值 = {@code System.currentTimeMillis() << 8 + random(0,255)}，
     * 保证多实例启动时序号空间近似单调且不易碰撞。
     * </p>
     */
    public SyncSequenceProvider() {
        long base = (System.currentTimeMillis() << 8) + RANDOM.nextInt(256);
        this.counter = new AtomicLong(base);
    }

    /**
     * 取下一个单调递增序号。
     *
     * @return 单调递增的序号
     */
    public long next() {
        return counter.incrementAndGet();
    }
}
