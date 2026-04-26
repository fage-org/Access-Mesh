package cn.ac.fage.accessmesh.permission.enums;

public enum ConfigType {
    SCOPE("SCOPE"),
    RELATION("RELATION"),
    BINDING("BINDING"),
    SUB_PERM("SUB_PERM");

    private final String value;

    ConfigType(String value) {
        this.value = value;
    }

    public String getValue() { return value; }

    public static ConfigType fromValue(String value) {
        for (ConfigType t : values()) {
            if (t.value.equals(value)) return t;
        }
        throw new IllegalArgumentException("Unknown ConfigType value: " + value);
    }
}
