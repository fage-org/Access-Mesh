package cn.ac.fage.accessmesh.permission.service;

import cn.ac.fage.accessmesh.permission.dto.req.BizDomainCreateReq;
import cn.ac.fage.accessmesh.permission.dto.req.DomainConfigReq;
import cn.ac.fage.accessmesh.permission.dto.req.ServiceConfigReq;
import cn.ac.fage.accessmesh.permission.dto.req.SystemConfigReq;
import cn.ac.fage.accessmesh.permission.dto.req.TypeCreateReq;
import cn.ac.fage.accessmesh.permission.dto.resp.*;

import java.util.List;

/**
 * Configuration management — all config-related CRUD.
 */
public interface ConfigManageService {

    // ===== TypeDefinition =====
    TypeDefinitionResp createType(TypeCreateReq req, Long operatorId);
    TypeDefinitionResp getType(Long tenantId, Long typeId);
    List<TypeDefinitionResp> listTypes(Long tenantId, Long bizDomainId);
    void deleteType(Long tenantId, Long typeId, Long operatorId);

    // ===== BizDomain =====
    BizDomainResp createBizDomain(BizDomainCreateReq req, Long operatorId);
    BizDomainResp getBizDomain(Long tenantId, Long domainId);
    List<BizDomainResp> listBizDomains(Long tenantId);
    void deleteBizDomain(Long tenantId, Long domainId, Long operatorId);

    // ===== DomainConfig =====
    void upsertDomainConfig(DomainConfigReq req);
    DomainConfigResp getDomainConfig(Long tenantId, Long bizDomainId, String configType);
    List<DomainConfigResp> listDomainConfigs(Long tenantId, Long bizDomainId);

    // ===== ServiceConfig =====
    ServiceConfigResp createServiceConfig(ServiceConfigReq req, Long operatorId);
    ServiceConfigResp getServiceConfig(Long tenantId, String serviceCode);
    List<ServiceConfigResp> listServiceConfigs(Long tenantId);
    void deleteServiceConfig(Long tenantId, String serviceCode, Long operatorId);

    // ===== SystemConfig =====
    void upsertSystemConfig(SystemConfigReq req);
    SystemConfigResp getSystemConfig(Long tenantId, String configKey);
    List<SystemConfigResp> listSystemConfigs(Long tenantId);
}
