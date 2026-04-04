package org.dromara.permission.model.permission;

import lombok.Getter;

@Getter
public enum PermissionErrorCode {
    INVALID_REQUEST("PERM_400", "Invalid request"),
    RESOURCE_NOT_FOUND("PERM_404_RESOURCE", "Resource not found"),
    OPERATION_NOT_FOUND("PERM_404_OPERATION", "Operation not found"),
    RESOURCE_OPERATION_TYPE_MISMATCH("PERM_400_TYPE_MISMATCH", "Resource type does not match operation type"),
    DOMAIN_SCOPE_NOT_ALLOWED("PERM_403_DOMAIN_SCOPE", "Domain scope is not allowed"),
    CONDITION_NOT_APPROVED("PERM_403_CONDITION", "Condition is not approved");

    private final String code;
    private final String message;

    PermissionErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
