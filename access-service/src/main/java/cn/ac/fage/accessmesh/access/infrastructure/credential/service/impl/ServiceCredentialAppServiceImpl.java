package cn.ac.fage.accessmesh.access.infrastructure.credential.service.impl;

import cn.ac.fage.accessmesh.access.audit.aop.OperationLog;
import cn.ac.fage.accessmesh.access.engine.constant.OperationCode;
import cn.ac.fage.accessmesh.access.engine.core.PermQueryEngine;
import cn.ac.fage.accessmesh.access.infrastructure.credential.dto.req.ServiceCredentialCreateReq;
import cn.ac.fage.accessmesh.access.infrastructure.credential.dto.req.ServiceCredentialListReq;
import cn.ac.fage.accessmesh.access.infrastructure.credential.dto.req.ServiceCredentialUpdateReq;
import cn.ac.fage.accessmesh.access.infrastructure.credential.dto.resp.ServiceCredentialCreateResp;
import cn.ac.fage.accessmesh.access.infrastructure.credential.dto.resp.ServiceCredentialResp;
import cn.ac.fage.accessmesh.access.infrastructure.credential.entity.ServiceCredential;
import cn.ac.fage.accessmesh.access.infrastructure.credential.service.ServiceCredentialAppService;
import cn.ac.fage.accessmesh.access.infrastructure.credential.service.domain.ServiceCredentialDomainService;
import cn.ac.fage.accessmesh.access.infrastructure.enums.AccessErrorCode;
import cn.ac.fage.accessmesh.access.infrastructure.util.OperatorContext;
import cn.ac.fage.accessmesh.access.infrastructure.util.OperatorUtil;
import cn.ac.fage.accessmesh.access.resource.service.domain.ServiceConfigDomainService;
import cn.ac.fage.accessmesh.access.type.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.common.enums.GlobalErrorCode;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.perm.common.dto.resp.ItemsResp;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 服务凭证管理应用服务实现（T-PERM-070）。
 * <p>门禁 service-config 管理面同族（写 SERVICE:MANAGE / 读 SERVICE:VIEW）；
 * 服务注册+启用校验对齐 20055 门禁的服务注册段（对齐 ResourceTypeOwnershipGuard 先例
 * 走 ServiceConfigDomainService，不直读 resource 包 mapper）。</p>
 */
@Service
public class ServiceCredentialAppServiceImpl implements ServiceCredentialAppService {

    private final ServiceCredentialDomainService serviceCredentialDomainService;
    private final ServiceConfigDomainService serviceConfigDomainService;
    private final PermQueryEngine engine;

    public ServiceCredentialAppServiceImpl(ServiceCredentialDomainService serviceCredentialDomainService,
                                           ServiceConfigDomainService serviceConfigDomainService,
                                           PermQueryEngine engine) {
        this.serviceCredentialDomainService = serviceCredentialDomainService;
        this.serviceConfigDomainService = serviceConfigDomainService;
        this.engine = engine;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "PERMISSION", action = "SERVICE_CREDENTIAL_CREATE",
        targetType = "service_credential", targetId = "#req.serviceCode()",
        summary = "'issue service credential for ' + #req.serviceCode()")
    public ServiceCredentialCreateResp create(Long tenantId, ServiceCredentialCreateReq req, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);
        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.SERVICE, null, OperationCode.MANAGE)) {
            throw new SecurityException("Permission denied: MANAGE on SERVICE");
        }
        // 绑定服务须已注册且启用（对齐 20055 门禁的服务注册段——签发凭证给未注册/停用服务
        // 会产出认证链恒 20068 拒绝的死凭证）
        requireActiveService(tenantId, req.serviceCode());

        ServiceCredentialDomainService.IssuedCredential issued =
            serviceCredentialDomainService.issue(tenantId, req.serviceCode(), req.expiresAt(), operatorId);
        return new ServiceCredentialCreateResp(issued.entity().getId(), issued.entity().getCredentialId(),
            issued.plainSecret(), issued.entity().getServiceCode(), issued.entity().getStatus(),
            issued.entity().getExpiresAt());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "PERMISSION", action = "SERVICE_CREDENTIAL_UPDATE",
        targetType = "service_credential", targetId = "#req.id()",
        summary = "'update service credential ' + #req.id()")
    public ServiceCredentialResp update(Long tenantId, ServiceCredentialUpdateReq req, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);
        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.SERVICE, null, OperationCode.MANAGE)) {
            throw new SecurityException("Permission denied: MANAGE on SERVICE");
        }
        if (req.status() == null && req.expiresAt() == null) {
            // 空 patch 拒绝（对齐 §7.8 用户 update 空档 90001 先例）
            throw new BizException(GlobalErrorCode.VALIDATION_FAILED.code(),
                GlobalErrorCode.VALIDATION_FAILED.message() + ": status 与 expiresAt 至少提供一项（null=不修改）");
        }
        if (req.status() != null) {
            int rows = serviceCredentialDomainService.changeStatus(tenantId, req.id(), req.status(), operatorId);
            if (rows == 0) {
                throw credentialNotFound();
            }
        }
        if (req.expiresAt() != null) {
            int rows = serviceCredentialDomainService.changeExpiresAt(tenantId, req.id(), req.expiresAt(), operatorId);
            if (rows == 0) {
                throw credentialNotFound();
            }
        }
        ServiceCredential updated = serviceCredentialDomainService.findById(tenantId, req.id());
        if (updated == null) {
            throw credentialNotFound();
        }
        return ServiceCredentialResp.from(updated);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "PERMISSION", action = "SERVICE_CREDENTIAL_REMOVE",
        targetType = "service_credential", targetId = "#id",
        summary = "'remove service credential ' + #id")
    public void remove(Long tenantId, Long id, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);
        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.SERVICE, null, OperationCode.MANAGE)) {
            throw new SecurityException("Permission denied: MANAGE on SERVICE");
        }
        int rows = serviceCredentialDomainService.remove(tenantId, id, operatorId);
        if (rows == 0) {
            throw credentialNotFound();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ItemsResp<ServiceCredentialResp> list(Long tenantId, ServiceCredentialListReq req) {
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.SERVICE, null, OperationCode.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on SERVICE");
        }
        List<ServiceCredentialResp> items = serviceCredentialDomainService.list(tenantId, req.normalizedServiceCode())
            .stream().map(ServiceCredentialResp::from).toList();
        return new ItemsResp<>(items);
    }

    /** 绑定服务注册+启用校验（未注册/停用→参数拒绝，防止签发恒拒绝的死凭证）。 */
    private void requireActiveService(Long tenantId, String serviceCode) {
        if (!ServiceConfigDomainService.isRegisteredAndEnabled(
                serviceConfigDomainService.selectByTenantAndServiceCode(tenantId, serviceCode))) {
            throw new BizException(AccessErrorCode.PERM_INVALID_PARAM.getCode(),
                AccessErrorCode.PERM_INVALID_PARAM.getMessage()
                    + ": 服务 " + serviceCode + " 未注册或已停用，凭证只能绑定已启用的注册服务");
        }
    }

    private static BizException credentialNotFound() {
        return new BizException(AccessErrorCode.PERM_INVALID_PARAM.getCode(),
            AccessErrorCode.PERM_INVALID_PARAM.getMessage() + ": 凭证不存在或已删除");
    }
}
