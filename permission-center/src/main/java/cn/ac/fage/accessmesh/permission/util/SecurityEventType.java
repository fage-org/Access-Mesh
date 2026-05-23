package cn.ac.fage.accessmesh.permission.util;

/**
 * 安全事件类型枚举
 * <p>
 * 定义安全日志记录的事件类型分类。
 * 用于SecurityLogUtil对安全事件进行分类记录。
 * 支持SIEM和日志分析系统的结构化日志格式。
 * </p>
 */
public enum SecurityEventType {

    /**
     * 请求被阻止
     * <p>
     * 表示请求因缺少或无效的内部API密钥而被阻止。
     * 用于检测未授权的API访问尝试。
     * </p>
     */
    BLOCKED_REQUEST,

    /**
     * 权限拒绝
     * <p>
     * 表示访问因权限不足而被拒绝。
     * 用于记录权限校验失败的事件。
     * </p>
     */
    PERMISSION_DENIED,

    /**
     * 无效签名
     * <p>
     * 表示用户身份请求头的签名验证失败。
     * 用于检测请求伪造或身份欺骗尝试。
     * </p>
     */
    INVALID_SIGNATURE,

    /**
     * 可疑输入
     * <p>
     * 表示检测到可疑输入（可能的注入攻击）。
     * 用于记录潜在的SQL注入、XSS等攻击尝试。
     * </p>
     */
    SUSPICIOUS_INPUT
}