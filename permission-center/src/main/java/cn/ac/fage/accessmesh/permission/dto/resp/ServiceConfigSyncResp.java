package cn.ac.fage.accessmesh.permission.dto.resp;

public record ServiceConfigSyncResp(
    int createdResources,
    int createdMappings,
    int updatedMappings,
    int deletedResources,
    int deletedMappings
) {}
