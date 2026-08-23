package cn.ac.fage.accessmesh.access.application.security;

import cn.ac.fage.accessmesh.access.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.permission.constant.LocalProjectionOwner;
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
        // T-PERM-042：业务编码语义批量门禁。code → entity 解析下沉引擎
        // （getDeniedResourceCodes 内部经 TypeResolutionService 批量解析，无 N+1）；
        // 未解析（无投影实体）的 code 进入拒绝集合 → fail-closed（与单条 forAuthCheck 内部解析语义一致）。
        Long tenantId = TenantContextHolder.getTenantId();
        Long operatorId = currentOperatorId();
        Long userId = typeResolutionService.resolveUserId(
            tenantId, LocalProjectionOwner.SUBJECT_LOCAL_USER, String.valueOf(operatorId));
        if (userId == null) {
            log.warn("Permission check user not found: operatorId={}, resourceType={}, resourceCodes={}, operation={}",
                operatorId, resourceTypeCode, resourceCodes, operationCode);
            throw new SecurityException("权限校验失败: 操作者主体不存在");
        }
        Set<String> deniedCodes = engine.getDeniedResourceCodes(
            tenantId, userId, resourceTypeCode, new LinkedHashSet<>(resourceCodes), operationCode);
        if (!deniedCodes.isEmpty()) {
            log.warn("Permission denied (batch): operatorId={}, resourceType={}, deniedCodes={}, operation={}",
                operatorId, resourceTypeCode, deniedCodes, operationCode);
            throw new SecurityException(
                String.format("权限被拒绝: 无法在 %s:%s 上执行 %s 操作。原因: %s",
                    resourceTypeCode, deniedCodes, operationCode, "NO_PERMISSION"));
        }
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
