package cn.ac.fage.accessmesh.example.enums;

import lombok.Getter;

/**
 * Example 服务错误码枚举
 * <p>
 * 定义 example-service 业务域错误码，范围 30001-39999（见 project-rules §1.2 错误码分段）。
 * 错误码分配规则：
 * <ul>
 *   <li>30001-30099：演示接口（demo）相关错误</li>
 *   <li>30101-30199：持久化权限演示业务错误</li>
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

    PERMISSION_DENIED(30004, "无业务操作权限（业务最终检查拒绝，T-ACCESS-061）"),

    PERM_CHECK_UNAVAILABLE(30005, "鉴权服务暂不可用，业务最终检查失败已拒绝（fail-closed）"),
    DEMO_OBJECT_UNAVAILABLE(30101, "报表或字段不可用"),
    DEMO_POLICY_INVALID(30102, "配置或资源类型用途无效"),
    DEMO_VERSION_CONFLICT(30103, "元数据版本或业务编码冲突"),
    DEMO_IMPORT_CONFLICT(30104, "导入请求号或有效记录冲突"),
    DEMO_CSV_INVALID(30105, "CSV 格式或字段无效"),
    DEMO_LIMIT_EXCEEDED(30106, "请求或执行容量超限"),
    DEMO_RESULT_UNAVAILABLE(30107, "执行结果尚不可用或已到期"),
    DEMO_RESOURCE_NOT_READY(30108, "当前资源版本尚未发布成功"),
    DEMO_TRIGGER_CONFLICT(30109, "执行请求号已绑定其他请求");

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
