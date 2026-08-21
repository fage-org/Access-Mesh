package cn.ac.fage.accessmesh.common.cache;

/**
 * 授权读取令牌（T-ACCESS-008 剩余 TTL 回填辅助）
 * <p>
 * 业务读路径在缓存 miss 后、开始数据库读取事务或快照查询<b>之前</b>调用
 * {@link CacheService#beginRead(CacheCatalogEntry)} 获取本令牌；令牌内部记录
 * 单调时钟起点。回填时使用 {@code CacheService.put(token, ...)} /
 * {@code putBatch(token, ...)}，框架自动计算「读取起点 + catalog 有效 TTL - 当前时刻」
 * 的剩余 TTL 并强制不超过 catalog TTL；剩余 ≤ 0 时不写入。
 * </p>
 * <p>
 * 单条、批量、并发合并和重试不得重新创建令牌（重置起点）——同一轮读取
 * 全程复用同一令牌。
 * </p>
 *
 * @param <V> 缓存值类型
 */
public final class CacheReadToken<V> {

    private final CacheCatalogEntry<V> catalog;
    private final long startNanos;

    CacheReadToken(CacheCatalogEntry<V> catalog, long startNanos) {
        this.catalog = catalog;
        this.startNanos = startNanos;
    }

    /**
     * 本次读取对应的缓存目录
     *
     * @return catalog
     */
    public CacheCatalogEntry<V> catalog() {
        return catalog;
    }

    /**
     * 单调时钟起点（纳秒）
     *
     * @return 起点
     */
    long startNanos() {
        return startNanos;
    }
}
