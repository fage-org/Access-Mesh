package org.dromara.auth.identity.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.auth.identity.service.IdentityKernelService;
import org.dromara.authcenter.api.enums.SubjectType;
import org.dromara.authcenter.api.model.DelegationContext;
import org.dromara.authcenter.api.model.PermissionVersionInfo;
import org.dromara.authcenter.api.model.PrincipalContext;
import org.dromara.authcenter.api.model.SubjectProfile;
import org.dromara.authcenter.api.request.DelegationIssueRequest;
import org.dromara.authcenter.api.request.DelegationRevokeRequest;
import org.dromara.authcenter.api.request.IdentityContextIssueRequest;
import org.dromara.authcenter.api.request.PermissionVersionQueryRequest;
import org.dromara.authcenter.api.request.SubjectQueryRequest;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 混合身份内核服务
 *
 * 集成 permission-center 的权限版本查询，支持登录时写入版本到令牌
 *
 * @author RuoYi-Cloud-Plus
 */
@Slf4j
@Primary
@Service
@RequiredArgsConstructor
public class HybridIdentityKernelService implements IdentityKernelService {

    private final InMemoryIdentityKernelService delegate;
    private final PermissionVersionClient permissionVersionClient;

    private final Map<String, DelegationContext> delegations = new ConcurrentHashMap<>();

    @Override
    public PrincipalContext issueContext(IdentityContextIssueRequest request) {
        PrincipalContext context = delegate.issueContext(request);

        // 如果请求中没有版本，从 permission-center 查询
        if (context.getPermissionVersion() == null || context.getPermissionVersion().isBlank()) {
            try {
                String version = queryPermissionVersion(request.getTenantId());
                context.setPermissionVersion(version);
            } catch (Exception e) {
                log.warn("Failed to query permission version for tenant {}: {}", request.getTenantId(), e.getMessage());
                context.setPermissionVersion(request.getTenantId() + "-v0");
            }
        }

        // 处理委托上下文
        if (request.getDelegationId() != null && !request.getDelegationId().isBlank()) {
            DelegationContext delegationContext = delegations.get(request.getDelegationId());
            if (delegationContext != null) {
                context.setDelegationContext(delegationContext);
                context.setSubjectType(SubjectType.DELEGATED);
            }
        }

        return context;
    }

    @Override
    public DelegationContext issueDelegation(DelegationIssueRequest request) {
        DelegationContext delegationContext = new DelegationContext();
        delegationContext.setDelegationId(UUID.randomUUID().toString());
        delegationContext.setServiceSubjectId(request.getServiceSubjectId());
        delegationContext.setDelegatedUserId(request.getDelegatedUserId());
        delegationContext.setIssuedAtEpochMilli(System.currentTimeMillis());
        delegationContext.setExpiresAtEpochMilli(request.getExpiresAtEpochMilli());
        delegationContext.setReason(request.getReason());
        delegations.put(delegationContext.getDelegationId(), delegationContext);
        log.info("Issued delegation {} for service {} on behalf of user {}", delegationContext.getDelegationId(),
            request.getServiceSubjectId(), request.getDelegatedUserId());
        return delegationContext;
    }

    @Override
    public void revokeDelegation(DelegationRevokeRequest request) {
        delegations.remove(request.getDelegationId());
        log.info("Revoked delegation {}", request.getDelegationId());
    }

    @Override
    public SubjectProfile getSubject(SubjectQueryRequest request) {
        return delegate.getSubject(request);
    }

    /**
     * 查询权限版本
     */
    public String queryPermissionVersion(String tenantId) {
        PermissionVersionInfo info = permissionVersionClient.queryVersion(tenantId);
        return info != null ? info.getPermissionVersion() : tenantId + "-v0";
    }
}
