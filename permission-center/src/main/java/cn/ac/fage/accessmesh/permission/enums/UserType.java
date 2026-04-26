package cn.ac.fage.accessmesh.permission.enums;

/**
 * UserType: 来自 type_definition 预置值
 */
public enum UserType {
    USER(1, "人员"),
    SERVICE(2, "服务");

    private final int value;
    private final String label;

    UserType(int value, String label) {
        this.value = value;
        this.label = label;
    }

    public int getValue() { return value; }
    public String getLabel() { return label; }

    public static UserType fromValue(int value) {
        for (UserType t : values()) {
            if (t.value == value) return t;
        }
        throw new IllegalArgumentException("Unknown UserType value: " + value);
    }
}
