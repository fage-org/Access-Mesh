package cn.ac.fage.accessmesh.permission.util;

import java.sql.SQLException;

/**
 * 数据库异常分类工具。
 */
public final class DatabaseExceptionSupport {

    /** PostgreSQL unique_violation。 */
    private static final String UNIQUE_VIOLATION_SQL_STATE = "23505";

    private DatabaseExceptionSupport() {
    }

    /**
     * 判断异常链是否表示唯一约束冲突。
     *
     * <p>优先使用 PostgreSQL 稳定的 SQLState 23505；约束名仅作为驱动未暴露
     * {@link SQLException} 时的兼容兜底，避免主要逻辑依赖异常消息格式。</p>
     *
     * @param error 异常链入口
     * @param constraintNames 兼容识别的约束名
     * @return 是否为唯一约束冲突
     */
    public static boolean isUniqueViolation(Throwable error, String... constraintNames) {
        Throwable cause = error;
        while (cause != null) {
            if (cause instanceof SQLException sqlException
                && UNIQUE_VIOLATION_SQL_STATE.equals(sqlException.getSQLState())) {
                return true;
            }
            String message = cause.getMessage();
            if (message != null) {
                for (String constraintName : constraintNames) {
                    if (constraintName != null && message.contains(constraintName)) {
                        return true;
                    }
                }
            }
            cause = cause.getCause();
        }
        return false;
    }
}
