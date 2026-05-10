package cn.ac.fage.accessmesh.permission;

/**
 * 权限中心错误码常量类
 * <p>
 * 定义权限中心的错误码范围（20001-29999）。
 * </p>
 */
public final class PermErrorCode {

    /**
     * 私有构造方法（常量类）
     */
    private PermErrorCode() {}

    // ===== 参数校验错误（20001-20099） =====

    /**
     * 参数无效
     */
    public static final int INVALID_PARAM = 20001;
    /**
     * 参数缺失
     */
    public static final int MISSING_PARAM = 20002;

    // ===== 未找到错误（20100-20199） =====

    /**
     * 用户未找到
     */
    public static final int USER_NOT_FOUND = 20100;
    /**
     * 角色未找到
     */
    public static final int ROLE_NOT_FOUND = 20101;
    /**
     * 资源未找到
     */
    public static final int RESOURCE_NOT_FOUND = 20102;
    /**
     * 操作未找到
     */
    public static final int OPERATION_NOT_FOUND = 20103;
    /**
     * 条件未找到
     */
    public static final int CONDITION_NOT_FOUND = 20104;
    /**
     * 依赖未找到
     */
    public static final int DEPENDENCY_NOT_FOUND = 20105;
    /**
     * 配置未找到
     */
    public static final int CONFIG_NOT_FOUND = 20106;
    /**
     * 业务域未找到
     */
    public static final int DOMAIN_NOT_FOUND = 20107;
    /**
     * 服务配置未找到
     */
    public static final int SERVICE_CONFIG_NOT_FOUND = 20108;
    /**
     * API映射未找到
     */
    public static final int API_MAPPING_NOT_FOUND = 20109;
    /**
     * 冲突规则未找到
     */
    public static final int CONFLICT_RULE_NOT_FOUND = 20110;
    /**
     * 类型未找到
     */
    public static final int TYPE_NOT_FOUND = 20111;

    // ===== 冲突/重复错误（20200-20299） =====

    /**
     * 用户已存在
     */
    public static final int USER_EXISTS = 20200;
    /**
     * 角色已存在
     */
    public static final int ROLE_EXISTS = 20201;
    /**
     * 角色已分配
     */
    public static final int ROLE_ALREADY_ASSIGNED = 20202;

    // ===== 类型解析错误（20300-20399） =====

    /**
     * 未知类型编码
     */
    public static final int UNKNOWN_TYPE_CODE = 20300;
    /**
     * 未知业务域编码
     */
    public static final int UNKNOWN_DOMAIN_CODE = 20301;
    /**
     * 类型不匹配
     */
    public static final int TYPE_MISMATCH = 20302;

    // ===== 状态错误（20400-20499） =====

    /**
     * 无效操作
     */
    public static final int INVALID_OPERATION = 20400;
    /**
     * 不支持的同步模式
     */
    public static final int SYNC_MODE_NOT_SUPPORTED = 20401;
}
