package cn.ac.fage.accessmesh.permission.util;

/**
 * 操作者工具类
 * <p>
 * 提供操作者ID解析的工具方法。
 * 支持显式指定或自动从上下文解析操作者身份。
 * </p>
 */
public final class OperatorUtil {

    /**
     * 私有构造函数
     * <p>
     * 工具类不允许实例化。
     * </p>
     */
    private OperatorUtil() {
        // 工具类
    }

    /**
     * 解析操作者ID
     * <p>
     * 如果提供了显式的操作者ID则直接使用，否则从上下文自动解析。
     * 用于需要灵活指定或自动获取操作者身份的场景。
     * </p>
     *
     * @param operatorId 显式指定的操作者ID，为null时自动从上下文解析
     * @return 解析后的操作者ID
     */
    public static Long resolveOrDefault(Long operatorId) {
        return operatorId != null ? operatorId : OperatorContext.getOperatorId();
    }
}