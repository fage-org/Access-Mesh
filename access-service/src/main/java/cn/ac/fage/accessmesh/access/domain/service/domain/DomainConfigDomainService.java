package cn.ac.fage.accessmesh.access.domain.service.domain;

import cn.ac.fage.accessmesh.access.domain.entity.DomainConfig;

import java.util.List;

/**
 * 业务域配置事实领域服务（Q-009 收敛产物，T-ACCESS-045）。
 * <p>
 * 承接他能力包（grant SUB_PERM 策略解析）对 {@code domain_config} 的读取。
 * 硬契约：仅依赖本包 mapper（Spring 无环）；无缓存直读；不声明独立事务（REQUIRED 跟随调用方）。
 * configType 过滤（SUB_PERM 等）由调用方按业务口径做（与原直读形态一致）。
 * </p>
 */
public interface DomainConfigDomainService {

    /**
     * 查询租户内全部业务域配置行（SUB_PERM 策略解析消费，全量装载后内存过滤）。
     *
     * @param tenantId 租户ID
     * @return 配置行列表
     */
    List<DomainConfig> selectByTenantId(Long tenantId);
}
