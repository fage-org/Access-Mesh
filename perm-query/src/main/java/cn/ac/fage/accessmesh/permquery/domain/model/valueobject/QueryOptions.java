package cn.ac.fage.accessmesh.permquery.domain.model.valueobject;

import java.util.Map;
import java.util.Set;

/**
 * 权限查询选项值对象
 * <p>
 * 封装查询行为选项，决定是否执行各类查询和评估。
 * </p>
 */
public record QueryOptions(
    boolean queryScopeAll,
    boolean queryInstance,
    boolean evaluateConditions,
    boolean evaluateConflicts,
    boolean loadRoles,
    boolean loadResources,
    boolean loadOperations,
    boolean loadConditions,
    Map<String, Object> context
) {

    /**
     * 全量加载 + 全量评估（完整查询）
     */
    public static QueryOptions full() {
        return new QueryOptions(
            true, true, true, true,
            true, true, true, true,
            Map.of()
        );
    }

    /**
     * 仅权限条目（最小输出）
     */
    public static QueryOptions minimal() {
        return new QueryOptions(
            true, true, false, false,
            false, false, false, false,
            Map.of()
        );
    }

    /**
     * 全量加载 + 无评估（权限视图）
     */
    public static QueryOptions forView() {
        return new QueryOptions(
            false, true, false, false,
            true, true, true, true,
            Map.of()
        );
    }

    /**
     * 仅实例级 + 不评估（资源过滤）
     */
    public static QueryOptions forFilter() {
        return new QueryOptions(
            false, true, false, false,
            false, true, true, false,
            Map.of()
        );
    }

    /**
     * 类型级优先 + 完整评估（权限校验）
     */
    public static QueryOptions forCheck() {
        return new QueryOptions(
            true, true, true, true,
            false, false, false, false,
            Map.of()
        );
    }

    /**
     * 类型级优先 + 无评估（快速校验）
     */
    public static QueryOptions forValidate() {
        return new QueryOptions(
            true, true, false, false,
            false, false, false, false,
            Map.of()
        );
    }

    /**
     * 带评估上下文的选项
     */
    public QueryOptions withContext(Map<String, Object> ctx) {
        return new QueryOptions(
            queryScopeAll, queryInstance, evaluateConditions, evaluateConflicts,
            loadRoles, loadResources, loadOperations, loadConditions,
            ctx != null ? ctx : Map.of()
        );
    }
}