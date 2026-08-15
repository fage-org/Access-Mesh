package cn.ac.fage.accessmesh.access.application.security;

import cn.ac.fage.accessmesh.access.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.permission.constant.LocalProjectionOwner;
import cn.ac.fage.accessmesh.access.permission.dto.query.PermQuery;
import cn.ac.fage.accessmesh.access.permission.dto.query.PermResult;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;
import cn.dev33.satoken.stp.StpUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 本地权限门禁：通过 PermQueryEngine 校验，不再 Feign 自调用。
 */
@Service
@Primary
public class AdminPermissionValidatorImpl implements AdminPermissionValidator {

    private static final Logger log = LoggerFactory.getLogger(AdminPermissionValidatorImpl.class);

    private final TypeResolutionService typeResolutionService;
    private final PermQueryEngine engine;

    public AdminPermissionValidatorImpl(TypeResolutionService typeResolutionService,
                                        PermQueryEngine engine) {
        this.typeResolutionService = typeResolutionService;
        this.engine = engine;
    }

    @Override
    public void checkTypeLevel(String resourceTypeCode, String operationCode) {
        checkAndThrow(resourceTypeCode, null, operationCode);
    }

    @Override
    public void checkInstanceLevel(String resourceTypeCode, String resourceCode, String operationCode) {
        checkAndThrow(resourceTypeCode, resourceCode, operationCode);
    }

    @Override
    public void checkBatchInstanceLevel(String resourceTypeCode, List<String> resourceCodes, String operationCode) {
        if (resourceCodes == null || resourceCodes.isEmpty()) {
            return;
        }
        // T-ACCESS-005 评审 P2：批量校验走 engine.getDeniedIds（一次解析操作者 + 一次角色解析 +
        // 批量实例级查询），替代循环单条 checkAndThrow 的 N 次查询放大
        Long tenantId = TenantContextHolder.getTenantId();
        Long operatorId = currentOperatorId();
        Long userId = typeResolutionService.resolveUserId(
            tenantId, LocalProjectionOwner.SUBJECT_ADMIN_USER, String.valueOf(operatorId));
        if (userId == null) {
            log.warn("Permission check user not found: operatorId={}, resourceType={}, resourceCodes={}, operation={}",
                operatorId, resourceTypeCode, resourceCodes, operationCode);
            throw new SecurityException("权限校验失败: 操作者主体不存在");
        }
        Set<String> denied = engine.getDeniedIds(
            tenantId, userId, resourceTypeCode, new LinkedHashSet<>(resourceCodes), operationCode);
        if (!denied.isEmpty()) {
            log.warn("Permission denied (batch): operatorId={}, resourceType={}, resourceCodes={}, operation={}",
                operatorId, resourceTypeCode, denied, operationCode);
            throw new SecurityException(
                String.format("权限被拒绝: 无法在 %s:%s 上执行 %s 操作。原因: %s",
                    operationCode, resourceTypeCode, denied, "NO_PERMISSION"));
        }
    }

    private void checkAndThrow(String resourceTypeCode, String resourceCode, String operationCode) {
        Long tenantId = TenantContextHolder.getTenantId();
        Long operatorId = currentOperatorId();
        Long userId = typeResolutionService.resolveUserId(
            tenantId, LocalProjectionOwner.SUBJECT_ADMIN_USER, String.valueOf(operatorId));
        if (userId == null) {
            log.warn("Permission check user not found: operatorId={}, resourceType={}, resourceCode={}, operation={}",
                operatorId, resourceTypeCode, resourceCode, operationCode);
            throw new SecurityException("权限校验失败: 操作者主体不存在");
        }
        PermQuery q = PermQuery.forAuthCheck(tenantId, userId, resourceTypeCode, resourceCode, operationCode);
        PermResult result = engine.query(q);
        if (result == null || !result.allowed()) {
            String reason = result != null ? result.reason() : "NO_PERMISSION";
            log.warn("Permission denied: operatorId={}, resourceType={}, resourceCode={}, operation={}, reason={}",
                operatorId, resourceTypeCode, resourceCode, operationCode, reason);
            throw new SecurityException(
                String.format("权限被拒绝: 无法在 %s:%s 上执行 %s 操作。原因: %s",
                    operationCode, resourceTypeCode, resourceCode, reason));
        }
    }

    private static Long currentOperatorId() {
        Long operatorId = AccessRequestContext.getOperatorId();
        if (operatorId != null) {
            return operatorId;
        }
        return StpUtil.getLoginIdAsLong();
    }
}
