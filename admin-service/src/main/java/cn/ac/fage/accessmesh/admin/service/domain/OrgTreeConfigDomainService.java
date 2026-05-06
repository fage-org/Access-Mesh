package cn.ac.fage.accessmesh.admin.service.domain;

import cn.ac.fage.accessmesh.admin.entity.SysOrgTreeConfig;

import java.util.List;

/**
 * 组织树配置领域服务
 * 封装组织树配置查询核心领域逻辑
 */
public interface OrgTreeConfigDomainService {

    /**
     * 查询租户的默认组织树配置列表
     *
     * @param tenantId 租户ID
     * @return 默认配置列表
     */
    List<SysOrgTreeConfig> findDefaultConfigs(Long tenantId);

    /**
     * 查询租户的所有组织树配置
     *
     * @param tenantId 租户ID
     * @return 配置列表
     */
    List<SysOrgTreeConfig> findAllByTenantId(Long tenantId);

    /**
     * 根据ID查询有效的组织树配置
     *
     * @param tenantId 租户ID
     * @param id       配置ID
     * @return 配置实体，不存在返回null
     */
    SysOrgTreeConfig selectValidById(Long tenantId, Long id);
}