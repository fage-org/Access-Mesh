package cn.ac.fage.accessmesh.common.enums;

/**
 * Error code ranges per PROJECT_RULES.md §1.2:
 *   10001–19999 admin-service
 *   20001–29999 permission-center
 *   30001–39999 example-service
 *   90001–99999   global system errors
 */
public enum GlobalErrorCode {

    VALIDATION_FAILED(90001, "参数校验失败"),
    SYSTEM_ERROR(99999, "系统异常");

    private final int code;
    private final String message;

    GlobalErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int code() { return code; }
    public String message() { return message; }
}
