package cn.ac.fage.accessmesh.permission.service.domain.sync;

import cn.ac.fage.accessmesh.permission.dto.resp.ServiceConfigSyncResp;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
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
@NoArgsConstructor
@AllArgsConstructor
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
}