package cn.ac.fage.accessmesh.permission.service.domain.sync;

import cn.ac.fage.accessmesh.permission.dto.resp.ServiceConfigSyncResp;

/**
 * 服务接口同步结果类
 * <p>
 * 用于统计服务接口同步操作的结果数据。
 * 包含创建、更新、删除的资源数和映射数。
 * 可转换为响应DTO返回给前端。
 * </p>
 */
public class SyncResult {
    /**
     * 创建的资源数量
     */
    private int createdResources;

    /**
     * 更新的资源数量
     */
    private int updatedResources;

    /**
     * 创建的映射数量
     */
    private int createdMappings;

    /**
     * 更新的映射数量
     */
    private int updatedMappings;

    /**
     * 删除的映射数量
     */
    private int deletedMappings;

    /**
     * 删除的资源数量
     */
    private int deletedResources;

    /**
     * 默认构造函数
     */
    public SyncResult() {
    }

    /**
     * 全参数构造函数
     *
     * @param createdResources  创建的资源数
     * @param updatedResources  更新的资源数
     * @param createdMappings   创建的映射数
     * @param updatedMappings   更新的映射数
     * @param deletedMappings   删除的映射数
     * @param deletedResources  删除的资源数
     */
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
     * 将结果转换为响应DTO
     *
     * @return 服务配置同步响应DTO
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

    // ===== Getters and Setters =====

    /**
     * 获取创建的资源数
     */
    public int getCreatedResources() { return createdResources; }

    /**
     * 设置创建的资源数
     */
    public void setCreatedResources(int createdResources) { this.createdResources = createdResources; }

    /**
     * 获取更新的资源数
     */
    public int getUpdatedResources() { return updatedResources; }

    /**
     * 设置更新的资源数
     */
    public void setUpdatedResources(int updatedResources) { this.updatedResources = updatedResources; }

    /**
     * 获取创建的映射数
     */
    public int getCreatedMappings() { return createdMappings; }

    /**
     * 设置创建的映射数
     */
    public void setCreatedMappings(int createdMappings) { this.createdMappings = createdMappings; }

    /**
     * 获取更新的映射数
     */
    public int getUpdatedMappings() { return updatedMappings; }

    /**
     * 设置更新的映射数
     */
    public void setUpdatedMappings(int updatedMappings) { this.updatedMappings = updatedMappings; }

    /**
     * 获取删除的映射数
     */
    public int getDeletedMappings() { return deletedMappings; }

    /**
     * 设置删除的映射数
     */
    public void setDeletedMappings(int deletedMappings) { this.deletedMappings = deletedMappings; }

    /**
     * 获取删除的资源数
     */
    public int getDeletedResources() { return deletedResources; }

    /**
     * 设置删除的资源数
     */
    public void setDeletedResources(int deletedResources) { this.deletedResources = deletedResources; }

    /**
     * 将另一个同步结果累加到当前结果
     *
     * @param other 另一个同步结果
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