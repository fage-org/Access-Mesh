package cn.ac.fage.accessmesh.access.infrastructure;

import cn.ac.fage.accessmesh.access.infrastructure.enums.AccessErrorCode;
import cn.ac.fage.accessmesh.access.tenant.service.TenantAccessDeniedException;
import cn.ac.fage.accessmesh.access.tenant.service.TenantGateUnavailableException;
import cn.ac.fage.accessmesh.common.model.R;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TenantAvailabilityExceptionHandler {

    @ExceptionHandler(TenantAccessDeniedException.class)
    public R<Void> denied(TenantAccessDeniedException exception, HttpServletResponse response) {
        response.setStatus(exception.status());
        return R.fail(exception.code(), exception.getMessage());
    }

    @ExceptionHandler(TenantGateUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public R<Void> unavailable(TenantGateUnavailableException exception) {
        return R.fail(AccessErrorCode.TENANT_GATE_NOT_READY.getCode(), exception.getMessage());
    }
}
