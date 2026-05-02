package cn.ac.fage.accessmesh.permission.enums;

public enum RoleType {
    ORG(1, "组织"),
    POSITION(2, "职位"),
    PERSONAL(3, "个人"),
    GROUP_ROLE(5, "分组角色"),
    BASIC_ROLE(6, "基本角色");

    private final int value;
    private final String label;

    RoleType(int value, String label) {
        this.value = value;
        this.label = label;
    }

    public int getValue() { return value; }
    public String getLabel() { return label; }

    public boolean supportsHierarchy() {
        return this == ORG || this == GROUP_ROLE;
    }

    public boolean canConfigurePermissions() {
        return this != GROUP_ROLE;
    }

    public static RoleType fromValue(int value) {
        for (RoleType t : values()) {
            if (t.value == value) return t;
        }
        throw new IllegalArgumentException("Unknown RoleType value: " + value);
    }

    public static String safeGetLabel(Integer value) {
        try {
            return fromValue(value != null ? value : 0).getLabel();
        } catch (IllegalArgumentException e) {
            return "";
        }
    }
}
