package cn.ac.fage.accessmesh.perm.client.enums;

/** SDK 验签错误；30003 保留原示例接口兼容编号。 */
public enum PermClientErrorCode {
    SIGNATURE_INVALID(30003, "身份请求头签名校验失败");

    private final int code;
    private final String message;
    PermClientErrorCode(int code, String message) { this.code = code; this.message = message; }
    public int getCode() { return code; }
    public String getMessage() { return message; }
}
