package cn.ac.fage.accessmesh.common.enums;

/**
 * 全局错误码枚举
 * <p>
 * 错误码范围定义（参考PROJECT_RULES.md §1.2；T-ACCESS-010：admin-service 与
 * permission-center 已归并为 access-service，错误码继续按业务域归属分段，不重编号）：
 * <ul>
 *   <li>10001–19999：管理域（原 admin-service，现 access-service admin 域）</li>
 *   <li>20001–29999：权限域（原 permission-center，现 access-service permission 域）</li>
 *   <li>30001–39999：example-service</li>
 *   <li>90001–99999：全局系统错误</li>
 * </ul>
 * </p>
 */
public enum GlobalErrorCode {

    /**
     * 参数校验失败
     */
    VALIDATION_FAILED(90001, "参数校验失败"),

    /**
     * 系统异常
     */
    SYSTEM_ERROR(99999, "系统异常");

    /**
     * 错误码
     */
    private final int code;

    /**
     * 错误消息
     */
    private final String message;

    /**
     * 构造错误码枚举
     *
     * @param code    错误码
     * @param message 错误消息
     */
    GlobalErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    /**
     * 获取错误码
     *
     * @return 错误码
     */
    public int code() { return code; }

    /**
     * 获取错误消息
     *
     * @return 错误消息
     */
    public String message() { return message; }
}