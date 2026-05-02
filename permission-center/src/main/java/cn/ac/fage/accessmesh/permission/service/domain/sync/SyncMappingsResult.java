package cn.ac.fage.accessmesh.permission.service.domain.sync;

/**
 * Result object for mapping synchronization.
 */
public class SyncMappingsResult {
    private int createdCount;
    private int updatedCount;
    private java.util.Set<String> incomingKeys;

    public SyncMappingsResult() {
        this.incomingKeys = new java.util.HashSet<>();
    }

    public SyncMappingsResult(int createdCount, int updatedCount, java.util.Set<String> incomingKeys) {
        this.createdCount = createdCount;
        this.updatedCount = updatedCount;
        this.incomingKeys = incomingKeys != null ? incomingKeys : new java.util.HashSet<>();
    }

    public int getCreatedCount() { return createdCount; }
    public void setCreatedCount(int createdCount) { this.createdCount = createdCount; }

    public int getUpdatedCount() { return updatedCount; }
    public void setUpdatedCount(int updatedCount) { this.updatedCount = updatedCount; }

    public java.util.Set<String> getIncomingKeys() { return incomingKeys; }
    public void setIncomingKeys(java.util.Set<String> incomingKeys) {
        this.incomingKeys = incomingKeys != null ? incomingKeys : new java.util.HashSet<>();
    }
}