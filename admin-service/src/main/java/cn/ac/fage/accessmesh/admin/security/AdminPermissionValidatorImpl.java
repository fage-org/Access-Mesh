package cn.ac.fage.accessmesh.admin.security;

import cn.ac.fage.accessmesh.admin.config.TenantContextHolder;
import cn.ac.fage.accessmesh.perm.client.feign.PermissionFeignClient;
import cn.ac.fage.accessmesh.perm.common.dto.req.AuthCheckReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.BatchAuthCheckReq;
import cn.ac.fage.accessmesh.perm.common.dto.resp.AuthCheckResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.BatchAuthCheckResp;
import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.dev33.satoken.stp.StpUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AdminPermissionValidator implementation.
 * Calls permission-center via Feign to check permissions.
 */
@Service
public class AdminPermissionValidatorImpl implements AdminPermissionValidator {

    private static final Logger log = LoggerFactory.getLogger(AdminPermissionValidatorImpl.class);

    /** Subject type code for admin-service users */
    private static final String SUBJECT_TYPE_CODE = "ADMIN_USER";

    private final PermissionFeignClient permissionFeignClient;

    public AdminPermissionValidatorImpl(PermissionFeignClient permissionFeignClient) {
        this.permissionFeignClient = permissionFeignClient;
    }

    @Override
    public void checkTypeLevel(String resourceTypeCode, String operationCode) {
        String subjectExternalId = String.valueOf(StpUtil.getLoginIdAsLong());

        AuthCheckReq req = new AuthCheckReq(
            SUBJECT_TYPE_CODE,
            subjectExternalId,
            resourceTypeCode,
            null,  // null for type-level (CREATE)
            operationCode,
            null,  // domainCode
            null,  // codeType
            null,  // inheritMode
            null   // context
        );

        checkAndThrow(req, resourceTypeCode, "*", operationCode);
    }

    @Override
    public void checkInstanceLevel(String resourceTypeCode, String resourceCode, String operationCode) {
        String subjectExternalId = String.valueOf(StpUtil.getLoginIdAsLong());

        AuthCheckReq req = new AuthCheckReq(
            SUBJECT_TYPE_CODE,
            subjectExternalId,
            resourceTypeCode,
            resourceCode,
            operationCode,
            null,
            null,
            null,
            null
        );

        checkAndThrow(req, resourceTypeCode, resourceCode, operationCode);
    }

    @Override
    public void checkBatchInstanceLevel(String resourceTypeCode, List<String> resourceCodes, String operationCode) {
        if (resourceCodes == null || resourceCodes.isEmpty()) {
            return;
        }

        String subjectExternalId = String.valueOf(StpUtil.getLoginIdAsLong());

        List<BatchAuthCheckReq.AuthCheckItem> items = resourceCodes.stream()
            .map(code -> new BatchAuthCheckReq.AuthCheckItem(
                resourceTypeCode,
                code,
                operationCode,
                null,
                null,
                null
            ))
            .collect(Collectors.toList());

        BatchAuthCheckReq req = new BatchAuthCheckReq(
            SUBJECT_TYPE_CODE,
            subjectExternalId,
            items,
            null
        );

        PermResult<BatchAuthCheckResp> result = permissionFeignClient.batchCheckAuth(req);

        if (!isSuccess(result)) {
            log.warn("Batch permission check failed: subject={}, resourceType={}, operation={}",
                subjectExternalId, resourceTypeCode, operationCode);
            throw new SecurityException("Permission check failed: " + result.message());
        }

        BatchAuthCheckResp resp = result.data();
        if (resp == null || resp.items() == null) {
            throw new SecurityException("Permission check returned empty response");
        }

        // Check all items for denied permissions
        List<BatchAuthCheckResp.AuthCheckItemResult> deniedItems = resp.items().stream()
            .filter(item -> !item.allowed())
            .collect(Collectors.toList());

        if (!deniedItems.isEmpty()) {
            String deniedCodes = deniedItems.stream()
                .map(BatchAuthCheckResp.AuthCheckItemResult::resourceCode)
                .collect(Collectors.joining(", "));
            log.warn("Permission denied for batch operation: resourceType={}, resourceCodes={}, operation={}, reason={}",
                resourceTypeCode, deniedCodes, operationCode,
                deniedItems.get(0).reason());
            throw new SecurityException(
                String.format("Permission denied for %s:%s on %s", operationCode, deniedCodes, resourceTypeCode));
        }
    }

    private void checkAndThrow(AuthCheckReq req, String resourceTypeCode, String resourceCode, String operationCode) {
        PermResult<AuthCheckResp> result = permissionFeignClient.checkAuth(req);

        if (!isSuccess(result)) {
            log.warn("Permission check request failed: subject={}, resourceType={}, resourceCode={}, operation={}",
                req.subjectExternalId(), resourceTypeCode, resourceCode, operationCode);
            throw new SecurityException("Permission check failed: " + result.message());
        }

        AuthCheckResp resp = result.data();
        if (resp == null || !resp.allowed()) {
            String reason = resp != null ? resp.reason() : "NO_PERMISSION";
            log.warn("Permission denied: subject={}, resourceType={}, resourceCode={}, operation={}, reason={}",
                req.subjectExternalId(), resourceTypeCode, resourceCode, operationCode, reason);
            throw new SecurityException(
                String.format("Permission denied: cannot perform %s on %s:%s. Reason: %s",
                    operationCode, resourceTypeCode, resourceCode, reason));
        }
    }

    private boolean isSuccess(PermResult<?> result) {
        return result != null && result.code() == 200;
    }
}