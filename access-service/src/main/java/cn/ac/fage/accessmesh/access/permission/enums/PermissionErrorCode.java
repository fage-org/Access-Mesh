package cn.ac.fage.accessmesh.access.permission.enums;

import lombok.Getter;

/**
 * Permission Center 错误码枚举
 * <p>
 * 定义 permission-center 模块业务错误码，范围 20001-29999。
 * 当前先补齐权限授予相关错误码，后续按模块逐步扩展。
 * </p>
 */
@Getter
public enum PermissionErrorCode {

    /**
     * 角色不存在
     */
    ROLE_NOT_FOUND(20001, "角色不存在"),

    /**
     * 授权请求不能为空
     */
    GRANT_REQUEST_EMPTY(20002, "授权请求不能为空"),

    /**
     * 角色已禁用
     */
    ROLE_DISABLED(20003, "角色已禁用"),

    /**
     * 资源不存在
     */
    RESOURCE_NOT_FOUND(20004, "资源不存在"),

    /**
     * 操作权限不存在
     */
    OPERATION_NOT_FOUND(20005, "操作权限不存在"),

    /**
     * 权限条件不存在
     */
    CONDITION_NOT_FOUND(20006, "权限条件不存在"),

    /**
     * 资源类型不存在
     */
    RESOURCE_TYPE_NOT_FOUND(20007, "资源类型不存在"),

    /**
     * 资源类型与操作权限不匹配
     */
    RESOURCE_TYPE_OPERATION_MISMATCH(20008, "资源类型与操作权限不匹配"),

    /**
     * 父权限不存在
     */
    PARENT_PERMISSION_NOT_FOUND(20009, "父权限不存在"),

    /**
     * 父权限不是顶层权限
     */
    PARENT_PERMISSION_NOT_TOP_LEVEL(20010, "父权限必须是顶层权限"),

    /**
     * 子权限资源类型不被允许
     */
    SUB_PERMISSION_RESOURCE_TYPE_NOT_ALLOWED(20011, "子权限资源类型不在允许范围内"),

    /**
     * scopeMode=INSTANCE 时缺少 resourceCode
     */
    RESOURCE_CODE_REQUIRED(20012, "scopeMode=INSTANCE 时 resourceCode 不能为空"),

    /**
     * 子权限不存在
     */
    CHILD_PERMISSION_NOT_FOUND(20013, "子权限不存在"),

    /**
     * 权限不是子权限
     */
    PERMISSION_NOT_CHILD(20014, "权限不是子权限"),

    /**
     * 用户不存在
     */
    USER_NOT_FOUND(20015, "用户不存在"),

    /**
     * 用户已存在
     */
    USER_ALREADY_EXISTS(20016, "用户已存在"),

    /**
     * 业务域不存在
     */
    DOMAIN_NOT_FOUND(20017, "业务域不存在"),

    /**
     * 域配置不存在
     */
    DOMAIN_CONFIG_NOT_FOUND(20018, "域配置不存在"),

    /**
     * 资源依赖不存在
     */
    DEPENDENCY_NOT_FOUND(20019, "资源依赖不存在"),

    /**
     * 冲突规则不存在
     */
    CONFLICT_RULE_NOT_FOUND(20020, "冲突规则不存在"),

    /**
     * 类型编码不存在
     */
    TYPE_CODE_NOT_FOUND(20021, "类型编码不存在"),

    /**
     * 角色类型不匹配
     */
    ROLE_TYPE_MISMATCH(20022, "角色类型不匹配"),

    /**
     * 请求参数不能为空
     */
    REQUEST_ITEMS_EMPTY(20023, "请求参数不能为空"),

    /**
     * 同步资源不存在
     */
    SYNC_RESOURCE_NOT_FOUND(20024, "同步资源不存在"),

    /**
     * 资源状态冲突
     */
    RESOURCE_STATE_CONFLICT(20025, "资源状态冲突"),

    /**
     * 系统初始化失败
     */
    SYSTEM_INIT_FAILED(20026, "系统初始化失败"),

    /**
     * 参数校验失败
     */
    VALIDATION_FAILED(20027, "参数校验失败"),

    /**
     * 类型定义不存在
     */
    TYPE_DEFINITION_NOT_FOUND(20028, "类型定义不存在"),

    /**
     * 用户角色关系不存在
     */
    USER_ROLE_RELATION_NOT_FOUND(20029, "用户角色关系不存在"),

    /**
     * 同步元数据 targetStatus 非法
     */
    SYNC_TARGET_STATUS_INVALID(20030, "同步 targetStatus 不在 entityKind 允许范围内"),

    /**
     * 条件规则不可下发 Gateway 评估
     * <p>
     * gatewayEvaluable=true 时，conditionRules.items[].type 必须全部在
     * {@code ConditionEvalUtils.GATEWAY_PUSHABLE_TYPES} 白名单内。
     * </p>
     */
    CONDITION_RULES_INVALID(20031, "条件规则不可下发 Gateway 评估"),

    /**
     * 等价冲突规则已存在
     */
    CONFLICT_RULE_DUPLICATE(20032, "等价冲突规则已存在"),

    /**
     * 同一角色的同一资源、操作、范围及父权限下已存在 MANUAL 直接授权
     */
    DIRECT_PERMISSION_CONFLICT(20033, "同一资源与操作已存在直接授权"),

    /**
     * 自动补全授权记录只读
     */
    AUTO_DEP_READONLY(20034, "自动补全授权记录只读"),

    /**
     * 权限记录不存在、已变化或不属于目标角色
     */
    PERMISSION_NOT_FOUND(20036, "权限记录不存在或已发生变化"),

    /**
     * 操作者不具备授权传递能力
     */
    GRANT_CANNOT_DELEGATE(20040, "当前操作者无权转授该权限"),

    /**
     * 条件权限不可设置为可转授
     */
    CONDITIONAL_PERMISSION_CANNOT_DELEGATE(20041, "条件权限不可转授");

    private final int code;
    private final String message;

    PermissionErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
