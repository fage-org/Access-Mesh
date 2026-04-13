package org.dromara.permission.constant;

public final class PermissionConstants {

    public static final Long NOT_DELETED = 0L;

    public static final String CONDITION_SOURCE_PRESET = "PRESET";

    public static final String CONDITION_SOURCE_CUSTOM = "CUSTOM";

    public static final String CONDITION_STATUS_APPROVED = "APPROVED";

    public static final String CONDITION_STATUS_PENDING = "PENDING";

    public static final String CONDITION_STATUS_REJECTED = "REJECTED";

    public static final Boolean CONDITION_ENABLED = Boolean.TRUE;

    /**
     * 类型定义 - 用户类型
     */
    public static final String TYPE_KEY_USER_TYPE = "user_type";

    /**
     * 类型定义 - 角色类型
     */
    public static final String TYPE_KEY_ROLE_TYPE = "role_type";

    /**
     * 类型定义 - 资源类型
     */
    public static final String TYPE_KEY_RESOURCE_TYPE = "resource_type";

    private PermissionConstants() {
    }
}
