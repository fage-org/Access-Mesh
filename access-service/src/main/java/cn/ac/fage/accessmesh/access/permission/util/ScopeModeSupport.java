package cn.ac.fage.accessmesh.access.permission.util;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.perm.common.enums.ScopeMode;
import cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode;

/**
 * 对外 scopeMode 协议与内部 scopeAll 存储字段之间的转换。
 */
public final class ScopeModeSupport {

    private ScopeModeSupport() {
    }

    public static ScopeMode fromScopeAll(Boolean scopeAll) {
        return Boolean.TRUE.equals(scopeAll) ? ScopeMode.ALL : ScopeMode.INSTANCE;
    }

    public static boolean toScopeAllForGrant(ScopeMode scopeMode, String resourceCode, String codeType) {
        validateGrantScopeMode(scopeMode, resourceCode, codeType);
        return ScopeMode.ALL.equals(scopeMode);
    }

    public static ScopeMode fromSnapshot(String scopeMode, Boolean scopeAll) {
        if (scopeMode != null && !scopeMode.isBlank()) {
            try {
                return ScopeMode.valueOf(scopeMode.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return scopeAll == null ? null : fromScopeAll(scopeAll);
    }

    private static void validateGrantScopeMode(ScopeMode scopeMode, String resourceCode, String codeType) {
        if (scopeMode == null) {
            throw validation("scopeMode is required");
        }
        if (scopeMode != ScopeMode.INSTANCE && scopeMode != ScopeMode.ALL) {
            throw validation("scopeMode only supports INSTANCE or ALL");
        }
        if (scopeMode == ScopeMode.INSTANCE) {
            if (isBlank(resourceCode)) {
                throw validation("resourceCode is required when scopeMode=INSTANCE");
            }
            if (isBlank(codeType)) {
                throw validation("codeType is required when scopeMode=INSTANCE");
            }
        }
        if (scopeMode == ScopeMode.ALL && (!isBlank(resourceCode) || !isBlank(codeType))) {
            throw validation("resourceCode/codeType must be empty when scopeMode=ALL");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static BizException validation(String message) {
        return new BizException(PermissionErrorCode.VALIDATION_FAILED.getCode(), message);
    }
}
