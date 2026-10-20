package cn.ac.fage.accessmesh.example.metadata;

/** Fixed entrance vocabulary; row and field policy bindings remain configurable. */
public enum DemoPermission {
    REPORT_CREATE("DEMO_REPORT", "CREATE"),
    REPORT_VIEW("DEMO_REPORT", "VIEW"),
    REPORT_UPDATE("DEMO_REPORT", "UPDATE"),
    REPORT_READ("DEMO_REPORT", "DATA_READ"),
    REPORT_EDIT("DEMO_REPORT", "DATA_EDIT"),
    REPORT_EXPORT("DEMO_REPORT", "EXPORT"),
    TASK_CREATE("DEMO_TASK", "CREATE"),
    TASK_UPDATE("DEMO_TASK", "UPDATE"),
    TASK_ENABLE("DEMO_TASK", "ENABLE"),
    TASK_TRIGGER("DEMO_TASK", "TRIGGER"),
    TASK_VIEW("DEMO_TASK", "VIEW"),
    POLICY_MANAGE("DEMO_POLICY", "MANAGE"),
    DATASET_MANAGE("DEMO_DATASET", "MANAGE");

    private final String type;
    private final String operation;
    DemoPermission(String type, String operation) { this.type=type; this.operation=operation; }
    public String type() { return type; }
    public String operation() { return operation; }
}
