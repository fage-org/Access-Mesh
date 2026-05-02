package cn.ac.fage.accessmesh.permission.service.domain.sync;

/**
 * Result object for resource synchronization.
 */
public class SyncResourcesResult {
    private int createdCount;
    private int updatedCount;
    private java.util.Set<Long> activeResourceIds;

    public SyncResourcesResult() {
        this.activeResourceIds = new java.util.HashSet<>();
    }

    public SyncResourcesResult(int createdCount, int updatedCount, java.util.Set<Long> activeResourceIds) {
        this.createdCount = createdCount;
        this.updatedCount = updatedCount;
        this.activeResourceIds = activeResourceIds != null ? activeResourceIds : new java.util.HashSet<>();
    }

    public int getCreatedCount() { return createdCount; }
    public void setCreatedCount(int createdCount) { this.createdCount = createdCount; }

    public int getUpdatedCount() { return updatedCount; }
    public void setUpdatedCount(int updatedCount) { this.updatedCount = updatedCount; }

    public java.util.Set<Long> getActiveResourceIds() { return activeResourceIds; }
    public void setActiveResourceIds(java.util.Set<Long> activeResourceIds) {
        this.activeResourceIds = activeResourceIds != null ? activeResourceIds : new java.util.HashSet<>();
    }
}