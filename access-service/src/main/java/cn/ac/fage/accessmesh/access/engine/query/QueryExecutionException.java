package cn.ac.fage.accessmesh.access.engine.query;

/**
 * 新查询执行的技术故障（T-PERM-088，设计 §3.4 X01/X02 面）。
 * <p>
 * DB／缓存无法可信回源、规则装载、描述读取等运行中技术失败统一包装为本异常
 * （cause 保留原异常），不当普通 DENY、空清单或半批成功返回。结构错误
 * （{@link QueryValidationException}）与未实现区域（UnsupportedOperationException）
 * 不属于技术故障，原样抛出、不包装。外部错误映射保留在适配层——本异常为引擎内部
 * 契约，不未经版本化扩散到普通 SDK。预算／deadline 超限（EngineLimits，设计 §5.5）
 * 由 T-PERM-093 引入配置后走同一失败边界：整体技术失败，不返回半份事实。
 * </p>
 */
public class QueryExecutionException extends RuntimeException {

    public QueryExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
