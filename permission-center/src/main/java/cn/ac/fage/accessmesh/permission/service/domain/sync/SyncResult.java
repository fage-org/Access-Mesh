package cn.ac.fage.accessmesh.permission.service.domain.sync;

import cn.ac.fage.accessmesh.permission.dto.resp.ServiceConfigSyncResp;
import lombok.Getter;
import lombok.Setter;

/**
 * 服务接口同步结果类
 * <p>
 * 用于统计服务接口同步操作的结果数据。
 * 包含创建、更新、删除的资源数和映射数。
 * 可转换为响应DTO返回给前端。
 * </p>
 */
@Getter
@Setter
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