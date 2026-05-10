package cn.ac.fage.accessmesh.permission.dto.resp;

/**
 * 服务配置同步响应体
 * <p>
 * 返回服务接口同步的结果统计，包括新增、更新、删除的资源和映射数量。
 * 用于服务接口同步接口的响应。
 * </p>
 *
 * @param createdResources  新增的资源数量
 * @param updatedResources  更新的资源数量
 * @param createdMappings   新增的API映射数量
 * @param updatedMappings   更新的API映射数量
 * @param deletedResources  删除的资源数量
 * @param deletedMappings   删除的API映射数量
 */
public record ServiceConfigSyncResp(
    int createdResources,
    int updatedResources,
    int createdMappings,
    int updatedMappings,
    int deletedResources,
    int deletedMappings
) {}