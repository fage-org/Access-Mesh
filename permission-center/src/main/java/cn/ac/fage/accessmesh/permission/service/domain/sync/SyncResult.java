package cn.ac.fage.accessmesh.permission.service.domain.sync;

import cn.ac.fage.accessmesh.permission.dto.resp.ServiceConfigSyncResp;

/**
 * Result object for service interface synchronization.
 * Contains statistics about the sync operation.
 */
public class SyncResult {
    private int createdResources;
    private int updatedResources;
    private int createdMappings;
    private int updatedMappings;
    private int deletedMappings;
    private int deletedResources;

    public SyncResult() {
    }

    public SyncResult(int createdResources, int updatedResources, int createdMappings,
                      int updatedMappings, int deletedMappings, int deletedResources) {
        this.createdResources = createdResources;
        this.updatedResources = updatedResources;
        this.createdMappings = createdMappings;
        this.updatedMappings = updatedMappings;
        this.deletedMappings = deletedMappings;
        this.deletedResources = deletedResources;
    }

    /**
     * Converts this result to a response DTO.
     */
    public ServiceConfigSyncResp toResponse() {
        return new ServiceConfigSyncResp(
            createdResources,
            updatedResources,
            createdMappings,
            updatedMappings,
            deletedResources,
            deletedMappings
        );
    }

    // Getters and setters
    public int getCreatedResources() { return createdResources; }
    public void setCreatedResources(int createdResources) { this.createdResources = createdResources; }

    public int getUpdatedResources() { return updatedResources; }
    public void setUpdatedResources(int updatedResources) { this.updatedResources = updatedResources; }

    public int getCreatedMappings() { return createdMappings; }
    public void setCreatedMappings(int createdMappings) { this.createdMappings = createdMappings; }

    public int getUpdatedMappings() { return updatedMappings; }
    public void setUpdatedMappings(int updatedMappings) { this.updatedMappings = updatedMappings; }

    public int getDeletedMappings() { return deletedMappings; }
    public void setDeletedMappings(int deletedMappings) { this.deletedMappings = deletedMappings; }

    public int getDeletedResources() { return deletedResources; }
    public void setDeletedResources(int deletedResources) { this.deletedResources = deletedResources; }

    /**
     * Adds the counts from another SyncResult to this one.
     */
    public void add(SyncResult other) {
        if (other == null) return;
        this.createdResources += other.createdResources;
        this.updatedResources += other.updatedResources;
        this.createdMappings += other.createdMappings;
        this.updatedMappings += other.updatedMappings;
        this.deletedMappings += other.deletedMappings;
        this.deletedResources += other.deletedResources;
    }
}