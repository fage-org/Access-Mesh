package org.dromara.permission.model.permission;

public class PermissionServiceException extends RuntimeException {

    private final PermissionErrorCode errorCode;

    public PermissionServiceException(PermissionErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public PermissionServiceException(PermissionErrorCode errorCode, String detail) {
        super(detail == null || detail.isBlank() ? errorCode.getMessage() : errorCode.getMessage() + ": " + detail);
        this.errorCode = errorCode;
    }

    public PermissionErrorCode getErrorCode() {
        return errorCode;
    }
}
