package cn.ac.fage.accessmesh.access.application.security;

import cn.ac.fage.accessmesh.access.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.permission.constant.LocalProjectionOwner;
import cn.ac.fage.accessmesh.access.permission.dto.query.PermQuery;
import cn.ac.fage.accessmesh.access.permission.dto.query.PermResult;
import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceResolveKey;
import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceResolveRequest;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;
import cn.dev33.satoken.stp.StpUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
        // 批量校验走 engine.getDeniedIds（一次解析操作者 + 一次角色解析 + 批量实例级查询）。
        //  修复：getDeniedIds 按 resource_entity.id（投影主键）查询，
        // 必须先批量解析业务键 → 投影 ID，denied 结果再映射回业务键；
        // 未解析（无投影实体）→ fail-closed 拒绝（与单条 forAuthCheck 内部解析语义一致）。
        Long tenantId = TenantContextHolder.getTenantId();
        Long operatorId = currentOperatorId();
        Long userId = typeResolutionService.resolveUserId(
            tenantId, LocalProjectionOwner.SUBJECT_ADMIN_USER, String.valueOf(operatorId));
        if (userId == null) {
            log.warn("Permission check user not found: operatorId={}, resourceType={}, resourceCodes={}, operation={}",
                operatorId, resourceTypeCode, resourceCodes, operationCode);
            throw new SecurityException("权限校验失败: 操作者主体不存在");
        }
        List<ResourceResolveRequest> requests = resourceCodes.stream()
            .map(code -> new ResourceResolveRequest(resourceTypeCode, code, null, null))
            .toList();
        Map<ResourceResolveKey, Long> resolved = typeResolutionService.batchResolveResourceIds(tenantId, requests);
        Map<String, Long> entityIdByCode = new LinkedHashMap<>();
        List<String> unresolved = new ArrayList<>();
        for (String code : resourceCodes) {
            Long entityId = resolved.get(new ResourceResolveKey(resourceTypeCode, code, null, null));
            if (entityId == null) {
                unresolved.add(code);
            } else {
                entityIdByCode.put(code, entityId);
            }
        }
        if (!unresolved.isEmpty()) {
            log.warn("Permission check resource projection missing: operatorId={}, resourceType={}, codes={}",
                operatorId, resourceTypeCode, unresolved);
            throw new SecurityException("权限校验失败: 资源投影不存在: " + unresolved);
        }
        Set<Long> deniedEntityIds = engine.getDeniedIds(
            tenantId, userId, resourceTypeCode, new LinkedHashSet<>(entityIdByCode.values()), operationCode);
        if (!deniedEntityIds.isEmpty()) {
            Set<String> deniedCodes = entityIdByCode.entrySet().stream()
                .filter(e -> deniedEntityIds.contains(e.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
            log.warn("Permission denied (batch): operatorId={}, resourceType={}, resourceCodes={}, operation={}",
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
                    resourceTypeCode, resourceCode, operationCode, reason));
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
