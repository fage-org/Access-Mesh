package org.dromara.permission.kernel.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.authcenter.api.model.CustomScopeDescriptor;
import org.dromara.authcenter.api.model.DataScopeDescriptor;
import org.dromara.authcenter.api.model.InterfaceDecisionResult;
import org.dromara.authcenter.api.model.InterfacePermissionRule;
import org.dromara.authcenter.api.model.InterfacePermissionSnapshot;
import org.dromara.authcenter.api.model.PermissionVersionInfo;
import org.dromara.authcenter.api.request.CatalogRegistrationRequest;
import org.dromara.authcenter.api.request.DataScopeQueryRequest;
import org.dromara.authcenter.api.request.InterfacePermissionDecisionRequest;
import org.dromara.authcenter.api.request.InterfacePermissionSnapshotRequest;
import org.dromara.authcenter.api.request.PermissionVersionQueryRequest;
import org.dromara.permission.domain.PcPermissionVersion;
import org.dromara.permission.kernel.service.InterfacePermissionRuleQueryService;
import org.dromara.permission.kernel.service.InterfaceSnapshotCache;
import org.dromara.permission.kernel.service.PermissionKernelService;
import org.dromara.permission.service.PermissionVersionService;
import org.springframework.context.annotation.Primary;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Service;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * 混合内核权限服务
 *
 * 当前阶段保留既有内存权限骨架，同时把版本查询切到持久化权限版本表。
 * 支持快照缓存，以版本号为主要失效依据。
 *
 * @author RuoYi-Cloud-Plus
 */
@Slf4j
@Primary
@Service
@RequiredArgsConstructor
public class HybridPermissionKernelService implements PermissionKernelService {

    private final PathPatternParser pathPatternParser = new PathPatternParser();
    private final InMemoryPermissionKernelService delegate;
    private final PermissionVersionService permissionVersionService;
    private final InterfacePermissionRuleQueryService interfaceRuleQueryService;
    private final DatabaseDataScopeQueryService dataScopeQueryService;
    private final InterfaceSnapshotCache snapshotCache;

    @Override
    public PermissionVersionInfo registerCatalog(CatalogRegistrationRequest request) {
        PermissionVersionInfo ignored = delegate.registerCatalog(request);
        Long tenantId = parseLong(request.getTenantId());
        if (tenantId == null) {
            return ignored;
        }
        PcPermissionVersion current = permissionVersionService.bumpVersion(tenantId, "catalog_registration", null, request.getServiceCode());
        return buildVersionInfo(request.getTenantId(), request.getServiceCode(), current);
    }

    @Override
    public InterfacePermissionSnapshot queryInterfaceSnapshot(InterfacePermissionSnapshotRequest request) {
        Long tenantId = parseLong(request.getPrincipalContext().getTenantId());
        Long abstractUserId = parseLong(request.getPrincipalContext().getSubjectId());
        if (tenantId == null || abstractUserId == null) {
            return delegate.queryInterfaceSnapshot(request);
        }

        // 先查询当前版本
        PcPermissionVersion current = permissionVersionService.queryCurrentVersion(tenantId);
        String versionToken = buildVersionToken(current);

        // 尝试从缓存获取
        InterfacePermissionSnapshot cached = snapshotCache.get(tenantId, abstractUserId, versionToken);
        if (cached != null) {
            log.debug("Snapshot cache hit for tenant={}, user={}, version={}", tenantId, abstractUserId, versionToken);
            return cached;
        }

        // 缓存未命中，组装快照
        log.debug("Snapshot cache miss for tenant={}, user={}, version={}", tenantId, abstractUserId, versionToken);
        InterfacePermissionSnapshot snapshot = new InterfacePermissionSnapshot();
        snapshot.setTenantId(request.getPrincipalContext().getTenantId());
        snapshot.setSubjectKey(request.getPrincipalContext().getSubjectKey());
        snapshot.setPermissionVersion(versionToken);
        snapshot.setGeneratedAtEpochMilli(toEpochMilli(current));
        snapshot.setRules(interfaceRuleQueryService.listRules(tenantId, abstractUserId));

        // 缓存快照
        snapshotCache.put(tenantId, abstractUserId, versionToken, snapshot);
        return snapshot;
    }

    @Override
    public InterfaceDecisionResult decideInterface(InterfacePermissionDecisionRequest request) {
        InterfacePermissionSnapshotRequest snapshotRequest = new InterfacePermissionSnapshotRequest();
        snapshotRequest.setPrincipalContext(request.getPrincipalContext());
        InterfacePermissionSnapshot snapshot = queryInterfaceSnapshot(snapshotRequest);
        Optional<InterfacePermissionRule> matchedRule = matchRule(snapshot, request.getServiceCode(),
            request.getRequestMethod(), request.getRequestPath());

        InterfaceDecisionResult result = new InterfaceDecisionResult();
        result.setAllowed(matchedRule.isPresent());
        result.setMatchedCapabilityCode(matchedRule.map(InterfacePermissionRule::getCapabilityCode).orElse(null));
        result.setReason(matchedRule.isPresent() ? "matched-interface-rule" : "no-interface-rule");
        result.setPermissionVersion(snapshot.getPermissionVersion());
        return result;
    }

    @Override
    public List<DataScopeDescriptor> queryDataScopes(DataScopeQueryRequest request) {
        Long tenantId = parseLong(request.getPrincipalContext().getTenantId());
        Long abstractUserId = parseLong(request.getPrincipalContext().getSubjectId());
        if (tenantId == null || abstractUserId == null) {
            return delegate.queryDataScopes(request);
        }
        return dataScopeQueryService.queryDataScopes(tenantId, abstractUserId, request.getCapabilityCode());
    }

    @Override
    public List<CustomScopeDescriptor> queryCustomScopes(DataScopeQueryRequest request) {
        return delegate.queryCustomScopes(request);
    }

    @Override
    public PermissionVersionInfo queryVersion(PermissionVersionQueryRequest request) {
        Long tenantId = parseLong(request.getTenantId());
        if (tenantId == null) {
            return delegate.queryVersion(request);
        }
        PcPermissionVersion current = permissionVersionService.queryCurrentVersion(tenantId);
        return buildVersionInfo(request.getTenantId(), request.getSubjectType() + ":" + request.getSubjectId(), current);
    }

    private PermissionVersionInfo buildVersionInfo(String tenantId, String subjectKey, PcPermissionVersion current) {
        PermissionVersionInfo info = new PermissionVersionInfo();
        info.setTenantId(tenantId);
        info.setSubjectKey(subjectKey);
        info.setPermissionVersion(buildVersionToken(current));
        info.setUpdatedAtEpochMilli(toEpochMilli(current));
        return info;
    }

    private String buildVersionToken(PcPermissionVersion current) {
        return current.getTenantId() + "-v" + current.getVersionNo();
    }

    private long toEpochMilli(PcPermissionVersion current) {
        return current.getUpdatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private Optional<InterfacePermissionRule> matchRule(InterfacePermissionSnapshot snapshot, String serviceCode,
                                                        String requestMethod, String requestPath) {
        return snapshot.getRules().stream()
            .filter(rule -> rule.getServiceCode() == null || rule.getServiceCode().equals(serviceCode))
            .filter(rule -> rule.getHttpMethod() == null || rule.getHttpMethod().equalsIgnoreCase(requestMethod))
            .filter(rule -> isPathMatch(rule.getPathPattern(), requestPath))
            .findFirst();
    }

    private boolean isPathMatch(String pathPattern, String requestPath) {
        if (pathPattern == null || pathPattern.isBlank()) {
            return false;
        }
        PathPattern pattern = pathPatternParser.parse(pathPattern);
        return pattern.matches(PathContainer.parsePath(requestPath));
    }

    private Long parseLong(String value) {
        try {
            return Long.valueOf(value);
        } catch (Exception ignored) {
            return null;
        }
    }
}
