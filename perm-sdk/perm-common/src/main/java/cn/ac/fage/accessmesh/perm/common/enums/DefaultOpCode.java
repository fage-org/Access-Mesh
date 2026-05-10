package cn.ac.fage.accessmesh.perm.common.enums;

/**
 * 默认操作码枚举
 * <p>
 * 定义资源权限授予的标准操作类型。
 * 包括查看、编辑和删除三种基本操作权限。
 * </p>
 */
public enum DefaultOpCode {
    /**
     * 查看操作权限
     */
    VIEW("VIEW"),
    /**
     * 编辑操作权限
     */
    EDIT("EDIT"),
    /**
     * 删除操作权限
     */
    DELETE("DELETE");

    private final String code;

    /**
     * 构造函数
     *
     * @param code 操作码字符串
     */
    DefaultOpCode(String code) {
        this.code = code;
    }

    /**
     * 获取操作码字符串
     *
     * @return 操作码
     */
    public String getCode() {
        return code;
    }
}
