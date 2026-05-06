package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.permission.dto.query.PermQuery;
import cn.ac.fage.accessmesh.permission.dto.query.PermResult;
import cn.ac.fage.accessmesh.permission.dto.req.AuthCheckReq;
import cn.ac.fage.accessmesh.permission.dto.req.BatchAuthCheckReq;
import cn.ac.fage.accessmesh.permission.dto.req.CheckInterfaceReq;
import cn.ac.fage.accessmesh.permission.dto.resp.AuthCheckResp;
import cn.ac.fage.accessmesh.permission.dto.resp.BatchAuthCheckResp;
import cn.ac.fage.accessmesh.permission.dto.resp.CheckInterfaceResp;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionCheckDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.permission.util.PermResultUtils;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Domain-level adapter delegating to PermQueryEngine.
 */
@Service
public class PermissionCheckDomainServiceImpl implements PermissionCheckDomainService {

    private final TypeResolutionService typeResolutionService;
    private final PermQueryEngine engine;

    public PermissionCheckDomainServiceImpl(TypeResolutionService typeResolutionService,
                                             PermQueryEngine engine) {
        this.typeResolutionService = typeResolutionService;
        this.engine = engine;
    }

    @Override
    public AuthCheckResp check(Long tenantId, AuthCheckReq req) {
        Long userId = typeResolutionService.resolveUserId(tenantId, req.subjectTypeCode(), req.subjectExternalId());
        if (userId == null) return AuthCheckResp.deny("USER_NOT_FOUND");
        PermQuery q = PermQuery.forAuthCheck(tenantId, userId,
            req.resourceTypeCode(), req.resourceCode(), req.operationCode());
        q.setCodeType(req.codeType());
        q.setInheritMode(req.inheritMode());
        q.setContext(req.context());
        return PermResultUtils.toAuthCheckResp(engine.query(q));
    }

    @Override
    public BatchAuthCheckResp batchCheck(Long tenantId, BatchAuthCheckReq req) {
        Long userId = typeResolutionService.resolveUserId(tenantId, req.subjectTypeCode(), req.subjectExternalId());
        if (userId == null) {
            return new BatchAuthCheckResp(req.items().stream()
                .map(item -> new BatchAuthCheckResp.AuthCheckItemResult(
                    item.resourceTypeCode(), item.resourceCode(), item.operationCode(), false, "USER_NOT_FOUND",
                    java.util.List.of(), java.util.List.of()))
                .toList());
        }
        var resultsByKey = new java.util.LinkedHashMap<String, PermResult>();
        for (var item : req.items()) {
            PermQuery q = PermQuery.forAuthCheck(tenantId, userId,
                item.resourceTypeCode(), item.resourceCode(), item.operationCode());
            q.setContext(req.context());
            String key = item.resourceCode() != null && !item.resourceCode().isBlank()
                ? item.resourceCode() : item.resourceTypeCode() + ":" + item.operationCode();
            resultsByKey.put(key, engine.query(q));
        }
        return PermResultUtils.toBatchAuthCheckResp(resultsByKey);
    }

    @Override
    public CheckInterfaceResp checkInterface(Long tenantId, CheckInterfaceReq req) {
        return CheckInterfaceResp.deny("NOT_IMPLEMENTED_IN_DOMAIN_LAYER");
    }

    @Override
    public AuthCheckResp checkInternal(Long tenantId, Long userId, Long resourceEntityId,
                                        Long operationPermissionId, Long bizDomainId,
                                        String inheritMode, Map<String, Object> context) {
        String resourceTypeCode = typeResolutionService.resolveTypeCode(tenantId, "resource_type",
            typeResolutionService.resolveTypeValue(tenantId, "resource_type", null));
        PermQuery q = PermQuery.forAuthCheck(tenantId, userId, resourceTypeCode, null, null);
        q.setOperationPermissionIds(java.util.Set.of(operationPermissionId));
        q.setResourceEntityIds(java.util.Set.of(resourceEntityId));
        q.setBizDomainId(bizDomainId);
        q.setInheritMode(inheritMode);
        q.setContext(context);
        q.setQueryScopeAll(true);
        q.setEarlyReturnOnScopeAll(true);
        return PermResultUtils.toAuthCheckResp(engine.query(q));
    }
}
