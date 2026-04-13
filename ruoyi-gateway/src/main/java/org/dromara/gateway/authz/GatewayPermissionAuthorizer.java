package org.dromara.gateway.authz;

import org.dromara.authcenter.api.model.InterfacePermissionSnapshot;
import org.dromara.authcenter.api.model.PrincipalContext;
import org.dromara.gateway.config.properties.PermissionAuthzProperties;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 网关接口鉴权入口
 *
 * @author RuoYi-Cloud-Plus
 */
@Slf4j
@Component
public class GatewayPermissionAuthorizer {

    private final PermissionAuthzProperties properties;
    private final PrincipalContextResolver principalContextResolver;
    private final PermissionSnapshotCache permissionSnapshotCache;
    private final PermissionRuleMatcher permissionRuleMatcher;

    public GatewayPermissionAuthorizer(PermissionAuthzProperties properties,
                                       PrincipalContextResolver principalContextResolver,
                                       PermissionSnapshotCache permissionSnapshotCache,
                                       PermissionRuleMatcher permissionRuleMatcher) {
        this.properties = properties;
        this.principalContextResolver = principalContextResolver;
        this.permissionSnapshotCache = permissionSnapshotCache;
        this.permissionRuleMatcher = permissionRuleMatcher;
    }

    public void authorize(ServerHttpRequest request) {
        if (!properties.isEnabled()) {
            return;
        }

        PrincipalContext principalContext = principalContextResolver.resolve(request);

        // 检查是否在灰度范围内
        String route = request.getPath().value();
        if (!properties.isInGrayscale(principalContext.getTenantId(), route)) {
            log.debug("Request not in grayscale scope: tenant={}, route={}",
                principalContext.getTenantId(), route);
            return;
        }

        // 从缓存获取快照
        InterfacePermissionSnapshot snapshot = permissionSnapshotCache.getSnapshot(principalContext);

        // 执行规则匹配
        boolean matched = permissionRuleMatcher.match(snapshot, principalContext.getServiceCode(),
            request.getMethod() == null ? "GET" : request.getMethod().name(), request.getPath().value()).isPresent();

        if (matched || properties.isFailOpen() || snapshot.getRules().isEmpty()) {
            log.debug("Permission check passed: tenant={}, subject={}, route={}",
                principalContext.getTenantId(), principalContext.getSubjectKey(), route);
            return;
        }

        log.warn("Permission denied: tenant={}, subject={}, route={}, serviceCode={}",
            principalContext.getTenantId(), principalContext.getSubjectKey(),
            route, principalContext.getServiceCode());
        throw new GatewayPermissionDeniedException("接口访问被权限快照拒绝");
    }
}
