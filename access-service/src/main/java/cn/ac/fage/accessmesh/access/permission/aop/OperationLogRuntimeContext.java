package cn.ac.fage.accessmesh.access.permission.aop;

/**
 * 运行期操作日志上下文。
 * <p>
 * 供 AppService 在方法体内补充入口日志的真实执行结果，
 * 避免 void 方法只能依赖入参近似描述。
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

    static Snapshot snapshot() {
        State state = STATE.get();
        return new Snapshot(
            state.skip,
            state.summaryOverride,
            state.targetTypeOverride,
            state.targetIdOverride
        );
    }

    public static void clear() {
        STATE.remove();
    }

    static final class Snapshot {
        private final boolean skip;
        private final String summaryOverride;
        private final String targetTypeOverride;
        private final String targetIdOverride;

        private Snapshot(boolean skip, String summaryOverride, String targetTypeOverride, String targetIdOverride) {
            this.skip = skip;
            this.summaryOverride = summaryOverride;
            this.targetTypeOverride = targetTypeOverride;
            this.targetIdOverride = targetIdOverride;
        }

        boolean skip() {
            return skip;
        }

        String summaryOverride() {
            return summaryOverride;
        }

        String targetTypeOverride() {
            return targetTypeOverride;
        }

        String targetIdOverride() {
            return targetIdOverride;
        }
    }

    private static final class State {
        private boolean skip;
        private String summaryOverride;
        private String targetTypeOverride;
        private String targetIdOverride;
    }
}