package cn.ac.fage.accessmesh.permission.service.domain;

import cn.ac.fage.accessmesh.permission.dto.query.DomainTypeFilter;
import cn.ac.fage.accessmesh.permission.enums.DomainQueryMode;

import java.util.Set;

/**
 * 域分类领域服务
 * <p>
 * 通过 domain_config CLASSIFY 配置确定业务域的分类范围。
 * 全局域(global=true)的范围隐式包含未被其他域认领的资源类型，无需配置CLASSIFY。
 * 管理查询按三种模式(ALL/GLOBAL_PLUS/DOMAIN_ONLY)过滤资源类型。
 * 权限查询管线不使用此服务。
 * </p>
 */
public interface DomainClassifyService {

    /**
     * 根据查询模式构建资源类型过滤条件
     *
     * @param tenantId   租户ID
     * @param mode       查询模式
     * @param domainCode 业务域编码，mode=ALL时可为null
     * @return 资源类型过滤条件
     */
    DomainTypeFilter buildTypeFilter(Long tenantId, DomainQueryMode mode, String domainCode);

    /**
     * 获取指定域声明的资源类型码集合
     * <p>
     * 全局域返回隐式计算结果（未被其他域认领的类型）。
     * 非全局域返回 CLASSIFY 配置中声明的类型码。
     * </p>
     *
     * @param tenantId   租户ID
     * @param domainCode 业务域编码
     * @return 资源类型码集合
     */
    Set<String> getClassifiedTypeCodes(Long tenantId, String domainCode);

    /**
     * 判断域查询模式是否覆盖指定资源类型码
     *
     * @param tenantId         租户ID
     * @param mode             查询模式
     * @param domainCode       业务域编码
     * @param resourceTypeCode 资源类型码
     * @return 覆盖返回true，否则返回false
     */
    boolean matchesTypeCode(Long tenantId, DomainQueryMode mode, String domainCode, String resourceTypeCode);

    /**
     * 通过资源类型码反查所属的业务域ID
     * <p>
     * 遍历所有非全局域的 CLASSIFY 配置，找到包含该类型码的域。
     * 如果多个域都包含该类型码，返回第一个匹配的域ID。
     * </p>
     *
     * @param tenantId         租户ID
     * @param resourceTypeCode 资源类型码
     * @return 业务域ID，未找到返回null
     */
    Long findDomainIdByTypeCode(Long tenantId, String resourceTypeCode);

    /**
     * 确保租户的全局域存在
     * <p>
     * 如果租户还没有全局域，自动创建一个。
     * 应在租户初始化时调用。
     * </p>
     *
     * @param tenantId 租户ID
     */
    void ensureGlobalDomain(Long tenantId);
}
