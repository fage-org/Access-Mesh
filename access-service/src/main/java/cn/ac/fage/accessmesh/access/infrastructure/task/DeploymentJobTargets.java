package cn.ac.fage.accessmesh.access.infrastructure.task;
public final class DeploymentJobTargets {
    public static final String AUDIT_RETENTION = "auditRetentionTask.cleanup";
    private DeploymentJobTargets() {}
    public static boolean isManaged(String target) {
        if (target == null) return false;
        // Match the existing invocation parser, including its discarded trailing separators.
        String[] parts = target.trim().split("\\.");
        return parts.length == 2 && "auditRetentionTask".equals(parts[0]) && "cleanup".equals(parts[1]);
    }
}
