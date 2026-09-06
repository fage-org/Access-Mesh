package cn.ac.fage.accessmesh.access.application.security;

import cn.ac.fage.accessmesh.access.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.permission.constant.LocalProjectionOwner;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.common.enums.GlobalErrorCode;
import cn.ac.fage.accessmesh.common.exception.SystemException;
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
    public boolean hasTypeLevel(String resourceTypeCode, String operationCode) {
        Long tenantId = TenantContextHolder.getTenantId();
        Long operatorId = currentOperatorId();
        Long userId;
        try {
            userId = typeResolutionService.resolveUserId(
                tenantId, LocalProjectionOwner.SUBJECT_LOCAL_USER, String.valueOf(operatorId));
            if (userId == null) {
                // 主体缺失归技术故障（fail-closed 抛 SystemException），不得当作「明确拒绝」返回 false
                // ——hasTypeLevel 的 false 语义仅限引擎成功响应且 allowed=false（P2-1）
                log.warn("Permission check user not found (hasTypeLevel): operatorId={}, resourceType={}, operation={}",
                    operatorId, resourceTypeCode, operationCode);
                throw new SystemException(GlobalErrorCode.SYSTEM_ERROR.code(),
                    "权限判定失败: 操作者主体不存在");
            }
            return engine.hasPermissionByCode(tenantId, userId, resourceTypeCode, null, operationCode);
        } catch (SystemException e) {
            throw e;
        } catch (RuntimeException e) {
            // 主体解析/引擎技术故障（如数据库异常）向上抛 SystemException，禁止静默降级为裁剪结果（P2-1）；
            // 不复用 SecurityException（全局映射 403）
            throw new SystemException(GlobalErrorCode.SYSTEM_ERROR.code(),
                "权限判定技术故障: " + e.getMessage(), e);
        }
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
        Set<String> deniedCodes = getDeniedResourceCodes(
            resourceTypeCode, new LinkedHashSet<>(resourceCodes), operationCode);
        if (!deniedCodes.isEmpty()) {
            Long operatorId = currentOperatorId();
            log.warn("Permission denied (batch): operatorId={}, resourceType={}, deniedCodes={}, operation={}",
                operatorId, resourceTypeCode, deniedCodes, operationCode);
            throw new SecurityException(
                String.format("权限被拒绝: 无法在 %s:%s 上执行 %s 操作。原因: %s",
                    resourceTypeCode, deniedCodes, operationCode, "NO_PERMISSION"));
        }
    }

    @Override
    public Set<String> getDeniedResourceCodes(String resourceTypeCode, Set<String> resourceCodes, String operationCode) {
        if (resourceCodes == null || resourceCodes.isEmpty()) {
            return Set.of();
        }
        Long tenantId = TenantContextHolder.getTenantId();
        Long operatorId = currentOperatorId();
        Long userId = typeResolutionService.resolveUserId(
            tenantId, LocalProjectionOwner.SUBJECT_LOCAL_USER, String.valueOf(operatorId));
        if (userId == null) {
            log.warn("Permission check user not found: operatorId={}, resourceType={}, resourceCodes={}, operation={}",
                operatorId, resourceTypeCode, resourceCodes, operationCode);
            throw new SecurityException("权限校验失败: 操作者主体不存在");
        }
        return engine.getDeniedResourceCodes(tenantId, userId, resourceTypeCode, resourceCodes, operationCode);
    }

    private void checkAndThrow(String resourceTypeCode, String resourceCode, String operationCode) {
        Long tenantId = TenantContextHolder.getTenantId();
        Long operatorId = currentOperatorId();
        Long userId = typeResolutionService.resolveUserId(
            tenantId, LocalProjectionOwner.SUBJECT_LOCAL_USER, String.valueOf(operatorId));
        if (userId == null) {
            log.warn("Permission check user not found: operatorId={}, resourceType={}, resourceCode={}, operation={}",
                operatorId, resourceTypeCode, resourceCode, operationCode);
            throw new SecurityException("权限校验失败: 操作者主体不存在");
        }
        // T-PERM-042 评审 P2：单点门禁按 admin contract §2 终态收敛到 engine.hasPermissionByCode
        //（forValidate 语义），不再构造 forAuthCheck + query 的第二套门禁语义
        if (!engine.hasPermissionByCode(tenantId, userId, resourceTypeCode, resourceCode, operationCode)) {
            log.warn("Permission denied: operatorId={}, resourceType={}, resourceCode={}, operation={}",
                operatorId, resourceTypeCode, resourceCode, operationCode);
            throw new SecurityException(
                String.format("权限被拒绝: 无法在 %s:%s 上执行 %s 操作。原因: %s",
                    resourceTypeCode, resourceCode, operationCode, "NO_PERMISSION"));
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
