package cn.ac.fage.accessmesh.permission.enums;

public enum ChangeSource {
    ADMIN("ADMIN"),
    SYNC("SYNC"),
    API("API"),
    SYSTEM("SYSTEM");

    private final String value;

    ChangeSource(String value) {
        this.value = value;
    }

    public String getValue() { return value; }

    public static ChangeSource fromValue(String value) {
        for (ChangeSource t : values()) {
            if (t.value.equals(value)) return t;
        }
        throw new IllegalArgumentException("Unknown ChangeSource value: " + value);
    }
}
