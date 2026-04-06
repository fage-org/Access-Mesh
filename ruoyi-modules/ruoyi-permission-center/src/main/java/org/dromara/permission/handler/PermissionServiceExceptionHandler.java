package org.dromara.permission.handler;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.constant.HttpStatus;
import org.dromara.common.core.domain.R;
import org.dromara.permission.model.permission.PermissionErrorCode;
import org.dromara.permission.model.permission.PermissionServiceException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class PermissionServiceExceptionHandler {

    @ExceptionHandler(PermissionServiceException.class)
    public R<Void> handlePermissionServiceException(PermissionServiceException e, HttpServletRequest request) {
        PermissionErrorCode errorCode = e.getErrorCode();
        int status = mapStatus(errorCode);
        String message = errorCode.getCode() + ": " + e.getMessage();
        log.error("request='{}', permission service error='{}'", request.getRequestURI(), message);
        R<Void> response = R.fail(status, message);
        response.setErrorCode(errorCode.getCode());
        return response;
    }

    private int mapStatus(PermissionErrorCode errorCode) {
        return switch (errorCode) {
            case INVALID_REQUEST, RESOURCE_OPERATION_TYPE_MISMATCH -> HttpStatus.BAD_REQUEST;
            case RESOURCE_NOT_FOUND, OPERATION_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case DOMAIN_SCOPE_NOT_ALLOWED, CONDITION_UNAVAILABLE -> HttpStatus.FORBIDDEN;
        };
    }
}
