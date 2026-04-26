package cn.ac.fage.accessmesh.perm.common.enums;

/**
 * Default operation codes for resource permission grants.
 */
public enum DefaultOpCode {
    VIEW("VIEW"),
    EDIT("EDIT"),
    DELETE("DELETE");

    private final String code;

    DefaultOpCode(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
