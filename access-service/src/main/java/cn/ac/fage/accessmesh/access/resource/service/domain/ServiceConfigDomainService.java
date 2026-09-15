package cn.ac.fage.accessmesh.access.resource.service.domain;

import cn.ac.fage.accessmesh.access.resource.entity.ServiceConfig;

/**
 * 服务配置事实领域服务（Q-009 收敛产物，T-ACCESS-045）。
 * <p>
 * 承接他能力包（type 所有权守卫）对 {@code service_config} 的读取。
 * 硬契约：仅依赖本包 mapper（Spring 无环）；无缓存直读；不声明独立事务（REQUIRED 跟随调用方）。
 * </p>
 */
public interface ServiceConfigDomainService {

    /**
     * 按租户与服务编码查服务配置行（注册状态判定消费：注册+未软删+启用由调用方按既出口径判）。
     *
     * @param tenantId    租户ID
     * @param serviceCode 服务编码
     * @return 服务配置实体，不存在返回 null
     */
    ServiceConfig selectByTenantAndServiceCode(Long tenantId, String serviceCode);
}
