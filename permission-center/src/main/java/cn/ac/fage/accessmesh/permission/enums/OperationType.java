package cn.ac.fage.accessmesh.permission.enums;

public enum OperationType {
    CREATE("CREATE"),
    UPDATE("UPDATE"),
    DELETE("DELETE"),
    ASSIGN("ASSIGN"),
    SYNC("SYNC"),
    REVOKE("REVOKE");

    private final String value;

    OperationType(String value) {
        this.value = value;
    }

    public String getValue() { return value; }

    public static OperationType fromValue(String value) {
        for (OperationType t : values()) {
            if (t.value.equals(value)) return t;
        }
        throw new IllegalArgumentException("Unknown OperationType value: " + value);
    }
}
