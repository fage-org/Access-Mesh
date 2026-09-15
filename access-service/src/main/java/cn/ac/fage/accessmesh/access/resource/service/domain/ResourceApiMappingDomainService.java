package cn.ac.fage.accessmesh.access.resource.service.domain;

import cn.ac.fage.accessmesh.access.resource.entity.ResourceApiMapping;

import java.util.List;
import java.util.Set;

/**
 * 资源 API 映射事实领域服务（Q-009 收敛产物，T-ACCESS-045）。
 * <p>
 * 承接他能力包（type 删除级联盘点 serviceCodes）对 {@code resource_api_mapping} 的读取。
 * 硬契约：仅依赖本包 mapper（Spring 无环）；无缓存直读；不声明独立事务（REQUIRED 跟随调用方）。
 * </p>
 */
public interface ResourceApiMappingDomainService {

    /**
     * 按资源实体 ID 集合查询 API 映射行（删除级联登记 markServiceCodes 消费）。
     *
     * @param tenantId         租户ID
     * @param resourceEntityIds 资源实体ID集合
     * @return API 映射列表
     */
    List<ResourceApiMapping> selectByResourceEntityIds(Long tenantId, Set<Long> resourceEntityIds);
}
