package cn.ac.fage.accessmesh.permission.service.domain;

import cn.ac.fage.accessmesh.permission.entity.DomainConfig;
import cn.ac.fage.accessmesh.permission.entity.BizDomain;
import cn.ac.fage.accessmesh.permission.entity.TypeDefinition;

import java.util.List;
import java.util.Set;

/**
 * 域配置领域服务接口
 * <p>
 * 提供域配置（DomainConfig）、业务域（BizDomain）、类型定义（TypeDefinition）
 * 的基础数据访问操作。这些实体是权限系统的基础配置数据，
 * 用于定义业务域、资源类型、角色类型等系统级配置。
 * 该接口封装统一的领域层访问API，供上层服务调用。
 * </p>
 */
public interface DomainConfigDomainService {

    // ===== 域配置操作 =====

    /**
     * 根据ID和租户ID查询有效域配置
     *
     * @param id       域配置ID
     * @param tenantId 租户ID
     * @return 域配置实体，不存在返回null
     */
    DomainConfig selectConfigById(Long id, Long tenantId);

    /**
     * 根据租户ID查询所有有效域配置列表
     *
     * @param tenantId 租户ID
     * @return 域配置列表
     */
    List<DomainConfig> selectConfigsByTenantId(Long tenantId);

    /**
     * 根据租户ID和业务域ID查询有效域配置列表
     *
     * @param tenantId    租户ID
     * @param bizDomainId 业务域ID
     * @return 域配置列表
     */
    List<DomainConfig> selectConfigsByTenantAndDomainId(Long tenantId, Long bizDomainId);

    /**
     * 根据租户ID、业务域ID和配置类型查询有效域配置
     *
     * @param tenantId    租户ID
     * @param bizDomainId 业务域ID
     * @param configType  配置类型值
     * @return 域配置实体，不存在返回null
     */
    DomainConfig selectConfigByType(Long tenantId, Long bizDomainId, Integer configType);

    /**
     * 根据租户ID统计有效域配置数量
     *
     * @param tenantId 租户ID
     * @return 匹配的域配置数量
     */
    long countConfigsByTenantId(Long tenantId);

    /**
     * 插入域配置
     *
     * @param entity 域配置实体
     */
    void insert(DomainConfig entity);

    /**
     * 更新域配置
     *
     * @param entity 域配置实体
     * @return 更新影响的行数
     */
    int update(DomainConfig entity);

    // ===== 业务域操作 =====

    /**
     * 根据ID和租户ID查询有效业务域
     *
     * @param id       业务域ID
     * @param tenantId 租户ID
     * @return 业务域实体，不存在返回null
     */
    BizDomain selectBizDomainById(Long id, Long tenantId);

    /**
     * 根据租户ID查询所有有效业务域列表
     *
     * @param tenantId 租户ID
     * @return 业务域列表
     */
    List<BizDomain> selectBizDomainsByTenantId(Long tenantId);

    /**
     * 根据租户ID和域编码查询有效业务域
     *
     * @param tenantId   租户ID
     * @param domainCode 域编码
     * @return 业务域实体，不存在返回null
     */
    BizDomain selectBizDomainByCode(Long tenantId, String domainCode);

    /**
     * 根据租户ID和域编码集合批量查询有效业务域
     *
     * @param tenantId    租户ID
     * @param domainCodes 域编码集合
     * @return 业务域列表
     */
    List<BizDomain> selectBizDomainsByCodes(Long tenantId, Set<String> domainCodes);

    /**
     * 插入业务域
     *
     * @param entity 业务域实体
     */
    void insertBizDomain(BizDomain entity);

    /**
     * 更新业务域
     *
     * @param entity 业务域实体
     * @return 更新影响的行数
     */
    int updateBizDomain(BizDomain entity);

    // ===== 类型定义操作 =====

    /**
     * 根据ID和租户ID查询有效类型定义
     *
     * @param id       类型定义ID
     * @param tenantId 租户ID
     * @return 类型定义实体，不存在返回null
     */
    TypeDefinition selectTypeDefinitionById(Long id, Long tenantId);

    /**
     * 根据租户ID查询所有有效类型定义列表
     *
     * @param tenantId 租户ID
     * @return 类型定义列表
     */
    List<TypeDefinition> selectTypeDefinitionsByTenantId(Long tenantId);

    /**
     * 根据租户ID、类型键和类型编码查询有效类型定义
     *
     * @param tenantId 租户ID
     * @param typeKey  类型键
     * @param typeCode 类型编码
     * @return 类型定义实体，不存在返回null
     */
    TypeDefinition selectTypeDefinitionByTypeKeyAndCode(Long tenantId, String typeKey, String typeCode);

    /**
     * 根据租户ID、类型键和类型编码集合批量查询有效类型定义
     *
     * @param tenantId 租户ID
     * @param typeKey  类型键
     * @param codes    类型编码集合
     * @return 类型定义列表
     */
    List<TypeDefinition> selectTypeDefinitionsByTypeKeyAndCodes(Long tenantId, String typeKey, Set<String> codes);

    /**
     * 插入类型定义
     *
     * @param entity 类型定义实体
     */
    void insertTypeDefinition(TypeDefinition entity);

    /**
     * 更新类型定义
     *
     * @param entity 类型定义实体
     * @return 更新影响的行数
     */
    int updateTypeDefinition(TypeDefinition entity);
}