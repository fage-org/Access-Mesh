package cn.ac.fage.accessmesh.permission.service;

import cn.ac.fage.accessmesh.permission.dto.req.BizDomainCreateReq;
import cn.ac.fage.accessmesh.permission.dto.req.BizDomainUpdateReq;
import cn.ac.fage.accessmesh.permission.dto.req.DomainConfigReq;
import cn.ac.fage.accessmesh.permission.dto.req.ServiceConfigReq;
import cn.ac.fage.accessmesh.permission.dto.req.ServiceConfigSyncReq;
import cn.ac.fage.accessmesh.permission.dto.req.SystemConfigReq;
import cn.ac.fage.accessmesh.permission.dto.req.TypeCreateReq;
import cn.ac.fage.accessmesh.permission.dto.req.TypeUpdateReq;
import cn.ac.fage.accessmesh.permission.dto.resp.ApiMappingResp;
import cn.ac.fage.accessmesh.permission.dto.resp.*;

import java.util.List;

/**
 * 配置管理服务接口
 * <p>
 * 提供所有配置相关的CRUD操作，包括类型定义、业务域、域配置、服务配置和系统配置。
 * </p>
 */
public interface ConfigManageService {

    // ===== TypeDefinition =====

    /**
     * 创建类型定义
     *
     * @param tenantId   租户ID
     * @param req        类型创建请求
     * @param operatorId 操作者ID
     * @return 创建的类型定义详情
     */
    TypeDefinitionResp createType(Long tenantId, TypeCreateReq req, Long operatorId);

    /**
     * 获取类型定义详情
     *
     * @param tenantId 租户ID
     * @param typeId   类型定义ID
     * @return 类型定义详情
     */
    TypeDefinitionResp getType(Long tenantId, Long typeId);

    /**
     * 查询类型定义列表
     *
     * @param tenantId   租户ID
     * @param domainCode 业务域编码，可选
     * @return 类型定义列表
     */
    List<TypeDefinitionResp> listTypes(Long tenantId, String domainCode);

    /**
     * 更新类型定义
     *
     * @param tenantId   租户ID
     * @param req        类型更新请求
     * @param operatorId 操作者ID
     * @return 更新后的类型定义详情
     */
    TypeDefinitionResp updateType(Long tenantId, TypeUpdateReq req, Long operatorId);

    /**
     * 批量删除类型定义
     *
     * @param tenantId   租户ID
     * @param ids        类型定义ID列表
     * @param operatorId 操作者ID
     */
    void deleteTypesByIds(Long tenantId, List<Long> ids, Long operatorId);

    // ===== BizDomain =====

    /**
     * 创建业务域
     *
     * @param tenantId   租户ID
     * @param req        业务域创建请求
     * @param operatorId 操作者ID
     * @return 创建的业务域详情
     */
    BizDomainResp createBizDomain(Long tenantId, BizDomainCreateReq req, Long operatorId);

    /**
     * 获取业务域详情
     *
     * @param tenantId 租户ID
     * @param domainId 业务域ID
     * @return 业务域详情
     */
    BizDomainResp getBizDomain(Long tenantId, Long domainId);

    /**
     * 查询业务域列表
     *
     * @param tenantId 租户ID
     * @return 业务域列表
     */
    List<BizDomainResp> listBizDomains(Long tenantId);

    /**
     * 更新业务域
     *
     * @param tenantId   租户ID
     * @param req        业务域更新请求
     * @param operatorId 操作者ID
     * @return 更新后的业务域详情
     */
    BizDomainResp updateBizDomain(Long tenantId, BizDomainUpdateReq req, Long operatorId);

    /**
     * 批量删除业务域
     *
     * @param tenantId   租户ID
     * @param ids        业务域ID列表
     * @param operatorId 操作者ID
     */
    void deleteBizDomainsByIds(Long tenantId, List<Long> ids, Long operatorId);

    // ===== DomainConfig =====

    /**
     * 保存或更新域配置
     *
     * @param tenantId 租户ID
     * @param req      域配置请求
     * @return 保存后的域配置详情
     */
    DomainConfigResp upsertDomainConfig(Long tenantId, DomainConfigReq req);

    /**
     * 获取域配置详情
     *
     * @param tenantId   租户ID
     * @param domainCode 业务域编码
     * @param configType 配置类型编码
     * @return 域配置详情
     */
    DomainConfigResp getDomainConfig(Long tenantId, String domainCode, String configType);

    /**
     * 查询域配置列表
     *
     * @param tenantId   租户ID
     * @param domainCode 业务域编码
     * @return 域配置列表
     */
    List<DomainConfigResp> listDomainConfigs(Long tenantId, String domainCode);

    /**
     * 批量删除域配置
     *
     * @param tenantId   租户ID
     * @param ids        配置ID列表
     * @param operatorId 操作者ID
     */
    void deleteDomainConfigsByIds(Long tenantId, List<Long> ids, Long operatorId);

    // ===== ServiceConfig =====

    /**
     * 保存服务配置
     *
     * @param tenantId   租户ID
     * @param req        服务配置请求
     * @param operatorId 操作者ID
     * @return 保存后的服务配置详情
     */
    ServiceConfigResp saveServiceConfig(Long tenantId, ServiceConfigReq req, Long operatorId);

    /**
     * 获取服务配置详情
     *
     * @param tenantId   租户ID
     * @param serviceCode 服务编码
     * @return 服务配置详情
     */
    ServiceConfigResp getServiceConfig(Long tenantId, String serviceCode);

    /**
     * 查询服务配置列表
     *
     * @param tenantId 租户ID
     * @return 服务配置列表
     */
    List<ServiceConfigResp> listServiceConfigs(Long tenantId);

    /**
     * 批量删除服务配置
     *
     * @param tenantId   租户ID
     * @param ids        服务配置ID列表
     * @param operatorId 操作者ID
     */
    void deleteServiceConfigsByIds(Long tenantId, List<Long> ids, Long operatorId);

    /**
     * 同步服务接口
     *
     * @param tenantId   租户ID
     * @param req        服务同步请求
     * @param operatorId 操作者ID
     * @return 同步结果统计
     */
    ServiceConfigSyncResp syncServiceInterfaces(Long tenantId, ServiceConfigSyncReq req, Long operatorId);

    /**
     * 查询服务的API列表
     *
     * @param tenantId   租户ID
     * @param serviceCode 服务编码
     * @return API映射列表
     */
    List<ApiMappingResp> listServiceApis(Long tenantId, String serviceCode);

    // ===== SystemConfig =====

    /**
     * 保存或更新系统配置
     *
     * @param tenantId 租户ID
     * @param req      系统配置请求
     * @return 保存后的系统配置详情
     */
    SystemConfigResp upsertSystemConfig(Long tenantId, SystemConfigReq req);

    /**
     * 获取系统配置详情
     *
     * @param tenantId 租户ID
     * @param configKey 配置键
     * @return 系统配置详情
     */
    SystemConfigResp getSystemConfig(Long tenantId, String configKey);

    /**
     * 查询系统配置列表
     *
     * @param tenantId 租户ID
     * @return 系统配置列表
     */
    List<SystemConfigResp> listSystemConfigs(Long tenantId);
}