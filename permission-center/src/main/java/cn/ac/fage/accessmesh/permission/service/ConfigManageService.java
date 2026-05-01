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
 * Configuration management — all config-related CRUD.
 */
public interface ConfigManageService {

    // ===== TypeDefinition =====
    TypeDefinitionResp createType(Long tenantId, TypeCreateReq req, Long operatorId);
    TypeDefinitionResp getType(Long tenantId, Long typeId);
    List<TypeDefinitionResp> listTypes(Long tenantId, String domainCode);
    TypeDefinitionResp updateType(Long tenantId, TypeUpdateReq req, Long operatorId);
    void deleteType(Long tenantId, Long typeId, Long operatorId);

    void deleteTypesByIds(Long tenantId, List<Long> ids, Long operatorId);

    // ===== BizDomain =====
    BizDomainResp createBizDomain(Long tenantId, BizDomainCreateReq req, Long operatorId);
    BizDomainResp getBizDomain(Long tenantId, Long domainId);
    List<BizDomainResp> listBizDomains(Long tenantId);
    BizDomainResp updateBizDomain(Long tenantId, BizDomainUpdateReq req, Long operatorId);
    void deleteBizDomain(Long tenantId, Long domainId, Long operatorId);

    void deleteBizDomainsByIds(Long tenantId, List<Long> ids, Long operatorId);

    // ===== DomainConfig =====
    DomainConfigResp upsertDomainConfig(Long tenantId, DomainConfigReq req);
    DomainConfigResp getDomainConfig(Long tenantId, String domainCode, String configType);
    List<DomainConfigResp> listDomainConfigs(Long tenantId, String domainCode);
    void deleteDomainConfigsByIds(Long tenantId, List<Long> ids, Long operatorId);

    // ===== ServiceConfig =====
    ServiceConfigResp saveServiceConfig(Long tenantId, ServiceConfigReq req, Long operatorId);
    ServiceConfigResp createServiceConfig(Long tenantId, ServiceConfigReq req, Long operatorId);
    ServiceConfigResp getServiceConfig(Long tenantId, String serviceCode);
    List<ServiceConfigResp> listServiceConfigs(Long tenantId);
    void deleteServiceConfigsByIds(Long tenantId, List<Long> ids, Long operatorId);
    ServiceConfigSyncResp syncServiceInterfaces(Long tenantId, ServiceConfigSyncReq req, Long operatorId);
    List<ApiMappingResp> listServiceApis(Long tenantId, String serviceCode);

    // ===== SystemConfig =====
    SystemConfigResp upsertSystemConfig(Long tenantId, SystemConfigReq req);
    SystemConfigResp getSystemConfig(Long tenantId, String configKey);
    List<SystemConfigResp> listSystemConfigs(Long tenantId);
}
