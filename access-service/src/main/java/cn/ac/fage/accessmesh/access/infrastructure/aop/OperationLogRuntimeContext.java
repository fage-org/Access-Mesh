package cn.ac.fage.accessmesh.access.infrastructure.aop;

/**
 * 运行期操作日志上下文。
 * <p>
 * 供 AppService 在方法体内补充入口日志的真实执行结果，
 * 避免 void 方法只能依赖入参近似描述。
 * 与 {@link OperationLog} 注解同属 {@code @OperationLog} 框架基础设施（infrastructure.aop），
 * admin 与 permission 两域均可安全引用，不构成域间横向依赖。
 * </p>
 */
public final class OperationLogRuntimeContext {

    private static final ThreadLocal<State> STATE = ThreadLocal.withInitial(State::new);

    private OperationLogRuntimeContext() {
    }

    public static void markSkip() {
        STATE.get().skip = true;
    }

    public static void setSummary(String summary) {
        if (summary == null || summary.isBlank()) {
            return;
        }
        STATE.get().summaryOverride = summary;
    }

    public static void setTargetType(String targetType) {
        if (targetType == null || targetType.isBlank()) {
            return;
        }
        STATE.get().targetTypeOverride = targetType;
    }

    public static void setTargetId(Long targetId) {
        if (targetId == null) {
            return;
        }
        STATE.get().targetIdOverride = targetId.toString();
    }

    public static void setTargetId(String targetId) {
        if (targetId == null || targetId.isBlank()) {
            return;
        }
        STATE.get().targetIdOverride = targetId;
    }

    /**
     * 设置租户 override（匿名认证派生端点如 OAuth2 token/revoke）。
     * <p>
     * 这些端点在方法返回前清理 {@code TenantContextHolder}（finally 兜底），
     * 切面记录时 holder 已为空；但审计不能因此丢失，方法体在拿到租户后
     * （如从授权码/刷新令牌/JWT 载荷解析）登记此处，切面解析租户时优先采用
     * （优先顺序：runtime override → 方法参数 tenantId → {@code TenantContextHolder}）。
     * 无租户可解析的端点不登记，切面维持跳过兜底（不写 tenant_id=null）。
     * </p>
     *
     * @param tenantId 租户ID
     */
    public static void setTenantId(Long tenantId) {
        if (tenantId == null || tenantId <= 0) {
            return;
        }
        STATE.get().tenantIdOverride = tenantId;
    }

    public static Snapshot snapshot() {
        State state = STATE.get();
        return new Snapshot(
            state.skip,
            state.summaryOverride,
            state.targetTypeOverride,
            state.targetIdOverride,
            state.tenantIdOverride
        );
    }

    public static void clear() {
        STATE.remove();
    }

    /** 运行期快照（供 {@link OperationLogAspect} 等基础设施读取；跨包 public） */
    public static final class Snapshot {
        private final boolean skip;
        private final String summaryOverride;
        private final String targetTypeOverride;
        private final String targetIdOverride;
        private final Long tenantIdOverride;

        public Snapshot(boolean skip, String summaryOverride, String targetTypeOverride,
                        String targetIdOverride, Long tenantIdOverride) {
            this.skip = skip;
            this.summaryOverride = summaryOverride;
            this.targetTypeOverride = targetTypeOverride;
            this.targetIdOverride = targetIdOverride;
            this.tenantIdOverride = tenantIdOverride;
        }

        public boolean skip() {
            return skip;
        }

        public String summaryOverride() {
            return summaryOverride;
        }

        public String targetTypeOverride() {
            return targetTypeOverride;
        }

        public String targetIdOverride() {
            return targetIdOverride;
        }

        public Long tenantIdOverride() {
            return tenantIdOverride;
        }
    }

    private static final class State {
        private boolean skip;
        private String summaryOverride;
        private String targetTypeOverride;
        private String targetIdOverride;
        private Long tenantIdOverride;
    }
}