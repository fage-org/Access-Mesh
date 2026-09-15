package cn.ac.fage.accessmesh.access.type.service.domain;

import cn.ac.fage.accessmesh.access.type.entity.TypeDefinition;

import java.util.List;

/**
 * 类型定义事实领域服务（Q-009 收敛产物，T-ACCESS-045）。
 * <p>
 * 承接他能力包（grant/domain）对 {@code type_definition} 的读取。
 * 硬契约：仅依赖本包 mapper（Spring 无环）；无缓存直读；不声明独立事务（REQUIRED 跟随调用方）。
 * </p>
 */
public interface TypeDefinitionDomainService {

    /**
     * 查询租户内全部有效类型定义行（跨 type_key）。
     *
     * @param tenantId 租户ID
     * @return 类型定义列表
     */
    List<TypeDefinition> selectValidByTenant(Long tenantId);

    /**
     * 按租户与 typeKey 查询类型定义行（domain 域分类消费：列 resource_type 全部 code）。
     *
     * @param tenantId 租户ID
     * @param typeKey  类型键
     * @return 类型定义列表
     */
    List<TypeDefinition> selectByTenantAndTypeKey(Long tenantId, String typeKey);
}
