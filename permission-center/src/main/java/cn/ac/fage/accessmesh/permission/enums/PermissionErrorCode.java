package cn.ac.fage.accessmesh.permission.enums;

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
     * scopeAll=false 时缺少 resourceCode
     */
    RESOURCE_CODE_REQUIRED(20012, "scopeAll=false 时 resourceCode 不能为空"),

    /**
     * 子权限不存在
     */
    CHILD_PERMISSION_NOT_FOUND(20013, "子权限不存在"),

    /**
     * 权限不是子权限
     */
    PERMISSION_NOT_CHILD(20014, "权限不是子权限");

    private final int code;
    private final String message;

    PermissionErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}