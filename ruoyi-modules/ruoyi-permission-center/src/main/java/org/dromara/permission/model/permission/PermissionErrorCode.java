package org.dromara.permission.model.permission;

import lombok.Getter;

@Getter
public enum PermissionErrorCode {
    INVALID_REQUEST("PERM-101", "Invalid request"),
    RESOURCE_NOT_FOUND("PERM-102", "Resource not found"),
    OPERATION_NOT_FOUND("PERM-103", "Operation not found"),
    RESOURCE_OPERATION_TYPE_MISMATCH("PERM-104", "Resource type does not match operation type"),
    DOMAIN_SCOPE_NOT_ALLOWED("PERM-105", "Domain scope is not allowed"),
    CONDITION_NOT_APPROVED("PERM-106", "Condition is not approved");

    private final String code;
    private final String message;

    PermissionErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
