package cn.ac.fage.accessmesh.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import cn.ac.fage.accessmesh.common.model.PermResult;
import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public PermResult<Void> handleBizException(BizException e) {
        log.warn("BizException: code={}, message={}", e.getErrorCode(), e.getMessage());
        return PermResult.error(e.getErrorCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public PermResult<Void> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        log.warn("Validation failed: {}", msg);
        return PermResult.error(90001, msg);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public PermResult<Void> handleConstraintViolation(ConstraintViolationException e) {
        log.warn("Constraint violation: {}", e.getMessage());
        return PermResult.error(90001, e.getMessage());
    }

    @ExceptionHandler(SystemException.class)
    public PermResult<Void> handleSystemException(SystemException e) {
        log.error("SystemException: code={}, message={}", e.getErrorCode(), e.getMessage(), e);
        return PermResult.error(e.getErrorCode(), "系统异常");
    }

    @ExceptionHandler(Exception.class)
    public PermResult<Void> handleException(Exception e) {
        log.error("Unexpected exception", e);
        return PermResult.error(99999, "系统异常");
    }
}
