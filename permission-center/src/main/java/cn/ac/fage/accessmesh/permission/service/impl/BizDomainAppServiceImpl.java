package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.permission.aop.OperationLog;
import cn.ac.fage.accessmesh.permission.aop.OperationLogRuntimeContext;
import cn.ac.fage.accessmesh.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.permission.dto.req.BizDomainCreateReq;
import cn.ac.fage.accessmesh.permission.dto.req.BizDomainUpdateReq;
import cn.ac.fage.accessmesh.permission.dto.resp.BizDomainResp;
import cn.ac.fage.accessmesh.permission.entity.BizDomain;
import cn.ac.fage.accessmesh.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.permission.mapper.BizDomainMapper;
import cn.ac.fage.accessmesh.permission.service.BizDomainAppService;
import cn.ac.fage.accessmesh.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.permission.util.OperatorContext;
import cn.ac.fage.accessmesh.permission.util.OperatorUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 业务域应用服务实现类
 * <p>
 * 提供业务域的CRUD操作。
 * 所有操作均通过PermQueryEngine进行权限校验。
 * </p>
 */
@Service
public class BizDomainAppServiceImpl implements BizDomainAppService {

    private final BizDomainMapper bizDomainMapper;
    private final PermQueryEngine engine;

    public BizDomainAppServiceImpl(BizDomainMapper bizDomainMapper,
                                    PermQueryEngine engine) {
        this.bizDomainMapper = bizDomainMapper;
        this.engine = engine;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "perm", action = "biz-domain-create", targetType = "biz_domain", targetId = "#result.id()", summary = "'create biz domain ' + #req.code()")
    public BizDomainResp createBizDomain(Long tenantId, BizDomainCreateReq req, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.SYSTEM_CONFIG, null, OperationCodeConstants.MANAGE)) {
            throw new SecurityException("No permission to create biz domain");
        }

        BizDomain domain = new BizDomain();
        domain.setTenantId(tenantId);
        domain.setCode(req.code());
        domain.setName(req.name());
        domain.setDescription(req.description());
        domain.setCreatedBy(operatorId);
        LocalDateTime now = LocalDateTime.now();
        domain.setCreatedAt(now);
        domain.setUpdatedAt(now);
        domain.setDeleteFlag(0L);
        bizDomainMapper.insert(domain);
        return toBizDomainResp(domain);
    }

    @Override
    @Transactional(readOnly = true)
    public BizDomainResp getBizDomain(Long tenantId, Long domainId) {
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.DOMAIN, domainId, OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on DOMAIN:" + domainId);
        }

        BizDomain domain = bizDomainMapper.selectValidById(domainId, tenantId);
        return domain != null ? toBizDomainResp(domain) : null;
    }

    @Override
    @Transactional(readOnly = true)
    public List<BizDomainResp> listBizDomains(Long tenantId) {
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.DOMAIN, null, OperationCodeConstants.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on DOMAIN");
        }

        return bizDomainMapper.selectByTenantId(tenantId).stream().map(this::toBizDomainResp).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "perm", action = "biz-domain-update", targetType = "biz_domain", targetId = "#req.domainId()", summary = "'update biz domain ' + #req.domainId()")
    public BizDomainResp updateBizDomain(Long tenantId, BizDomainUpdateReq req, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.SYSTEM_CONFIG, null, OperationCodeConstants.MANAGE)) {
            throw new SecurityException("No permission to update biz domain");
        }

        BizDomain domain = bizDomainMapper.selectValidById(req.domainId(), tenantId);
        if (domain == null) throw new BizException(PermissionErrorCode.DOMAIN_NOT_FOUND.getCode(), "BizDomain not found: " + req.domainId());
        if (req.name() != null) domain.setName(req.name());
        if (req.description() != null) domain.setDescription(req.description());
        domain.setUpdatedAt(LocalDateTime.now());
        bizDomainMapper.update(domain);
        return toBizDomainResp(domain);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "perm", action = "biz-domain-remove", targetType = "BATCH", targetId = "", summary = "'batch remove biz domains'")
    public void deleteBizDomainsByIds(Long tenantId, List<Long> ids, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.SYSTEM_CONFIG, null, OperationCodeConstants.MANAGE)) {
            throw new SecurityException("No permission to delete biz domains");
        }

        if (ids == null || ids.isEmpty()) {
            OperationLogRuntimeContext.markSkip();
            return;
        }

        Set<Long> validInputIds = ids.stream()
            .filter(id -> id != null)
            .collect(Collectors.toSet());

        if (validInputIds.isEmpty()) {
            OperationLogRuntimeContext.markSkip();
            return;
        }

        List<BizDomain> entities = bizDomainMapper.selectValidByIds(tenantId, validInputIds);

        if (entities.isEmpty()) {
            OperationLogRuntimeContext.markSkip();
            return;
        }

        Set<Long> validIds = entities.stream()
            .map(BizDomain::getId)
            .collect(Collectors.toSet());

        LocalDateTime now = LocalDateTime.now();
        bizDomainMapper.softDeleteBatch(tenantId, new java.util.ArrayList<>(validIds), now);
        OperationLogRuntimeContext.setSummary("soft-deleted " + validIds.size() + " biz_domain row(s)");
    }

    private BizDomainResp toBizDomainResp(BizDomain d) {
        return new BizDomainResp(
            d.getId(), d.getTenantId(), d.getCode(),
            d.getName(), d.getDescription(), d.getCreatedAt()
        );
    }
}
