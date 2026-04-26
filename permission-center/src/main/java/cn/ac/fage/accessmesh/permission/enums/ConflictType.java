package cn.ac.fage.accessmesh.permission.enums;

public enum ConflictType {
    ROLE_MUTEX("ROLE_MUTEX", "角色互斥"),
    PERM_MUTEX("PERM_MUTEX", "权限互斥");

    private final String value;
    private final String label;

    ConflictType(String value, String label) {
        this.value = value;
        this.label = label;
    }

    public String getValue() { return value; }
    public String getLabel() { return label; }

    public static ConflictType fromValue(String value) {
        for (ConflictType t : values()) {
            if (t.value.equals(value)) return t;
        }
        throw new IllegalArgumentException("Unknown ConflictType value: " + value);
    }
}
