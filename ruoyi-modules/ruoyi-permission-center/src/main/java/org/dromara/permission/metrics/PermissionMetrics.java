package org.dromara.permission.metrics;

/**
 * 权限中心指标名称常量
 *
 * @author RuoYi-Cloud-Plus
 */
public final class PermissionMetrics {

    private PermissionMetrics() {
    }

    // ==================== 鉴权指标 ====================

    /**
     * 鉴权请求总数
     */
    public static final String CHECK_REQUESTS = "permission.check.requests";

    /**
     * 鉴权耗时
     */
    public static final String CHECK_DURATION = "permission.check.duration";

    /**
     * 鉴权拒绝数
     */
    public static final String CHECK_DENIALS = "permission.check.denials";

    // ==================== 快照指标 ====================

    /**
     * 快照请求总数
     */
    public static final String SNAPSHOT_REQUESTS = "permission.snapshot.requests";

    /**
     * 快照耗时
     */
    public static final String SNAPSHOT_DURATION = "permission.snapshot.duration";

    /**
     * 快照缓存命中
     */
    public static final String SNAPSHOT_CACHE_HITS = "permission.snapshot.cache.hits";

    /**
     * 快照缓存未命中
     */
    public static final String SNAPSHOT_CACHE_MISSES = "permission.snapshot.cache.misses";

    /**
     * 快照缓存大小
     */
    public static final String SNAPSHOT_CACHE_SIZE = "permission.snapshot.cache.size";

    // ==================== 版本指标 ====================

    /**
     * 版本查询请求总数
     */
    public static final String VERSION_REQUESTS = "permission.version.requests";

    /**
     * 版本查询失败数
     */
    public static final String VERSION_FAILURES = "permission.version.failures";

    /**
     * 版本变更数
     */
    public static final String VERSION_CHANGES = "permission.version.changes";

    // ==================== 条件指标 ====================

    /**
     * 条件求值请求总数
     */
    public static final String CONDITION_EVALUATIONS = "permission.condition.evaluations";

    /**
     * 条件求值异常数
     */
    public static final String CONDITION_ERRORS = "permission.condition.errors";

    /**
     * 条件求值耗时
     */
    public static final String CONDITION_DURATION = "permission.condition.duration";

    // ==================== 冲突指标 ====================

    /**
     * 冲突检测请求总数
     */
    public static final String CONFLICT_CHECKS = "permission.conflict.checks";

    /**
     * 冲突命中数
     */
    public static final String CONFLICT_HITS = "permission.conflict.hits";

    // ==================== 依赖指标 ====================

    /**
     * 依赖检查请求总数
     */
    public static final String DEPENDENCY_CHECKS = "permission.dependency.checks";

    /**
     * 依赖检查失败数
     */
    public static final String DEPENDENCY_FAILURES = "permission.dependency.failures";

    // ==================== 审计指标 ====================

    /**
     * 审计写入请求总数
     */
    public static final String AUDIT_WRITES = "permission.audit.writes";

    /**
     * 审计写入失败数
     */
    public static final String AUDIT_FAILURES = "permission.audit.failures";

    // ==================== 标签名称 ====================

    /**
     * 租户ID标签
     */
    public static final String TAG_TENANT_ID = "tenant.id";

    /**
     * 结果标签
     */
    public static final String TAG_RESULT = "result";

    /**
     * 状态标签
     */
    public static final String TAG_STATUS = "status";

    /**
     * 条件类型标签
     */
    public static final String TAG_CONDITION_TYPE = "condition.type";

    /**
     * 资源类型标签
     */
    public static final String TAG_RESOURCE_TYPE = "resource.type";

    /**
     * 拒绝原因标签
     */
    public static final String TAG_DENY_REASON = "deny.reason";
}
