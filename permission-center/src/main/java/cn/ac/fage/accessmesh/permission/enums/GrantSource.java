package cn.ac.fage.accessmesh.permission.enums;

public enum GrantSource {
    MANUAL("MANUAL"),
    AUTO_DEP("AUTO_DEP");

    private final String value;

    GrantSource(String value) {
        this.value = value;
    }

    public String getValue() { return value; }

    public static GrantSource fromValue(String value) {
        for (GrantSource t : values()) {
            if (t.value.equals(value)) return t;
        }
        throw new IllegalArgumentException("Unknown GrantSource value: " + value);
    }
}
