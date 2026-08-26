package cn.ac.fage.accessmesh.example.enums;

import lombok.Getter;

/**
 * Example 服务错误码枚举
 * <p>
 * 定义 example-service 业务域错误码，范围 30001-39999（见 project-rules §1.2 错误码分段）。
 * 错误码分配规则：
 * <ul>
 *   <li>30001-30099：演示接口（demo）相关错误</li>
 * </ul>
 * </p>
 */
@Getter
public enum ExampleErrorCode {

    /**
     * 演示接口参数无效（name 为空白）
     */
    DEMO_PARAM_INVALID(30001, "演示接口参数无效：name 不能为空白"),

    /**
     * 网关身份头缺失（X-User-Id / X-Tenant-Id 未注入，链路异常）
     */
    DEMO_IDENTITY_HEADER_MISSING(30002, "网关身份请求头缺失，请经 Gateway 访问本接口"),

    /**
     * 网关身份签名校验失败（X-User-Signature 缺失/不匹配/时间戳超窗，或本服务签名密钥未配置）
     */
    SIGNATURE_INVALID(30003, "身份请求头签名校验失败");

    /**
     * 构造错误码
     *
     * @param code    错误码
     * @param message 错误消息
     */
    ExampleErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    private final int code;
    private final String message;
}
