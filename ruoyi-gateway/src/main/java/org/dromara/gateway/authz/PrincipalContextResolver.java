package org.dromara.gateway.authz;

import cn.dev33.satoken.stp.StpUtil;
import org.dromara.authcenter.api.enums.SubjectType;
import org.dromara.authcenter.api.model.PrincipalContext;
import org.dromara.gateway.config.properties.PermissionAuthzProperties;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * 网关请求主体上下文解析器
 *
 * 从请求头和 Sa-Token 扩展信息中解析主体上下文
 *
 * @author RuoYi-Cloud-Plus
 */
@Component
public class PrincipalContextResolver {

    private final PermissionAuthzProperties properties;

    /**
     * Sa-Token 扩展信息键：权限版本
     */
    private static final String PERMISSION_VERSION_KEY = "permissionVersion";

    /**
     * Sa-Token 扩展信息键：抽象用户ID
     */
    private static final String ABSTRACT_USER_ID_KEY = "abstractUserId";

    public PrincipalContextResolver(PermissionAuthzProperties properties) {
        this.properties = properties;
    }

    public PrincipalContext resolve(ServerHttpRequest request) {
        PrincipalContext context = new PrincipalContext();
        context.setTenantId(firstNonBlank(
            request.getHeaders().getFirst(properties.getTenantHeader()),
            extraAsString("tenantId"),
            "000000"));
        context.setSubjectId(firstNonBlank(
            extraAsString(ABSTRACT_USER_ID_KEY),
            safeLoginId(),
            "anonymous"));
        context.setSubjectType(SubjectType.fromCode(
            firstNonBlank(request.getHeaders().getFirst(properties.getSubjectTypeHeader()), "USER")));
        context.setPermissionVersion(firstNonBlank(
            request.getHeaders().getFirst(properties.getPermissionVersionHeader()),
            extraAsString(PERMISSION_VERSION_KEY),
            context.getTenantId() + "-v0"));
        context.setServiceCode(resolveServiceCode(request));
        return context;
    }

    private String resolveServiceCode(ServerHttpRequest request) {
        String headerServiceCode = request.getHeaders().getFirst(properties.getServiceCodeHeader());
        if (headerServiceCode != null && !headerServiceCode.isBlank()) {
            return headerServiceCode;
        }
        List<String> segments = request.getPath().elements().stream()
            .map(PathContainer.Element::value)
            .filter(value -> value != null && !value.isBlank() && !Objects.equals(value, "/"))
            .map(value -> value.startsWith("/") ? value.substring(1) : value)
            .filter(value -> !value.isBlank())
            .toList();
        return segments.isEmpty() ? "unknown-service" : segments.get(0);
    }

    private String extraAsString(String key) {
        Object extra = StpUtil.getExtra(key);
        return extra == null ? null : String.valueOf(extra);
    }

    private String safeLoginId() {
        try {
            return StpUtil.getLoginIdAsString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
