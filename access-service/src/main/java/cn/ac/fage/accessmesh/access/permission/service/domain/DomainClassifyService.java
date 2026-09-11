package cn.ac.fage.accessmesh.access.permission.service.domain;

import cn.ac.fage.accessmesh.access.permission.enums.DomainQueryMode;

import java.util.Set;
import java.util.Map;

/**
 * 域分类领域服务
 * <p>
 * 通过 domain_config CLASSIFY 配置确定业务域的分类范围。
 * 全局域(global=true)的范围=有 CLASSIFY 声明按声明（T-PERM-046 定案 2026-09-09），无声明为未被其他域认领的资源类型动态补集。
 * 管理查询按三种模式(ALL/GLOBAL_PLUS/DOMAIN_ONLY)过滤资源类型。
 * 权限查询管线不使用此服务。
 * </p>
 */
public interface DomainClassifyService {

    /**
     * 获取指定域声明的资源类型码集合
     * <p>
     * 全局域：有 CLASSIFY 声明时返回声明集（T-PERM-046 定案 2026-09-09，可为空集），
     * 无声明时返回动态补集（未被其他域认领的类型）。
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
     * 批量预载域查询模式覆盖的资源类型码集合（T-PERM-055）
     * <p>
     * 与 {@link #matchesTypeCode} 同语义的批量形态：一次预载模式实际覆盖的类型码集合，
     * 供批量上下文循环内以 {@code Set.contains} 复用判定，消除逐条目调用 matchesTypeCode
     * 的点查放大。返回集合只含 type_definition 中实际存在的有效 resource_type 类型码
     * （未知类型码不在集合中，与 matchesTypeCode 的有效性前置判定一致）。
     * 批量上下文（逐条目循环/列表过滤）必须走本方法；单次调用场景可继续用 matchesTypeCode。
     * </p>
     *
     * @param tenantId   租户ID
     * @param mode       查询模式
     * @param domainCode 业务域编码
     * @return 模式覆盖的资源类型码集合（域不存在返回空集）；不保证可变性，调用方不得修改
     */
    Set<String> preloadCoveredTypeCodes(Long tenantId, DomainQueryMode mode, String domainCode);

    /**
     * 批量反查资源类型所属业务域，未被具体域认领的类型归入全局域。
     *
     * @param tenantId         租户ID
     * @param resourceTypeCodes 资源类型码集合
     * @return 资源类型码（原输入值）到业务域ID的映射
     */
    Map<String, Long> findDomainIdsByTypeCodes(Long tenantId, Set<String> resourceTypeCodes);
}
