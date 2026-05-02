package cn.ac.fage.accessmesh.permission.enums;

public enum ResourceType {
    MENU(1, "菜单"),
    BUTTON(2, "按钮"),
    API(3, "接口"),
    DATA(4, "数据");

    private final int value;
    private final String label;

    ResourceType(int value, String label) {
        this.value = value;
        this.label = label;
    }

    public int getValue() { return value; }
    public String getLabel() { return label; }

    public static ResourceType fromValue(int value) {
        for (ResourceType t : values()) {
            if (t.value == value) return t;
        }
        throw new IllegalArgumentException("Unknown ResourceType value: " + value);
    }

    public static String safeGetLabel(Integer value) {
        try {
            return fromValue(value != null ? value : 0).getLabel();
        } catch (IllegalArgumentException e) {
            return "";
        }
    }
}
