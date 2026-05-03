package cn.ac.fage.accessmesh.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.common.enums.GlobalErrorCode;
import jakarta.validation.ConstraintViolationException;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 全局异常处理器。
 * 统一处理各类异常，返回标准响应格式。
 * 注意：不向客户端暴露异常堆栈信息。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 业务异常处理。
     * 返回业务错误码和消息。
     */
    @ExceptionHandler(BizException.class)
    public PermResult<Void> handleBizException(BizException e) {
        log.warn("BizException: code={}, message={}", e.getErrorCode(), e.getMessage());
        return PermResult.error(e.getErrorCode(), e.getMessage());
    }

    /**
     * 系统异常处理。
     * 记录完整堆栈，返回通用错误消息。
     */
    @ExceptionHandler(SystemException.class)
    public PermResult<Void> handleSystemException(SystemException e) {
        log.error("SystemException: code={}, message={}", e.getErrorCode(), e.getMessage(), e);
        return PermResult.error(e.getErrorCode(), "系统异常");
    }

    /**
     * 安全异常处理。
     * 权限不足或安全违规时触发，返回 403 错误码。
     */
    @ExceptionHandler(SecurityException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public PermResult<Void> handleSecurityException(SecurityException e) {
        log.warn("SecurityException: {}", e.getMessage());
        return PermResult.error(403, "权限不足");
    }

    /**
     * 参数校验异常处理（@Valid 校验失败）。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public PermResult<Void> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        log.warn("Validation failed: {}", msg);
        return PermResult.error(GlobalErrorCode.VALIDATION_FAILED.code(), msg);
    }

    /**
     * 约束违规异常处理。
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public PermResult<Void> handleConstraintViolation(ConstraintViolationException e) {
        log.warn("Constraint violation: {}", e.getMessage());
        return PermResult.error(GlobalErrorCode.VALIDATION_FAILED.code(), e.getMessage());
    }

    /**
     * 非法参数异常处理。
     * 不向客户端暴露具体异常消息，返回通用错误提示。
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public PermResult<Void> handleIllegalArgumentException(IllegalArgumentException e, HttpServletRequest request) {
        String requestId = request.getHeader("X-Request-Id");
        log.warn("IllegalArgumentException [requestId={}]: {}", requestId, e.getMessage());
        return PermResult.error(400, "参数错误", requestId);
    }

    /**
     * JSON 解析异常处理。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public PermResult<Void> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("HttpMessageNotReadable: {}", e.getMessage());
        return PermResult.error(GlobalErrorCode.VALIDATION_FAILED.code(), "请求体格式错误");
    }

    /**
     * 通用异常处理。
     * 作为兜底，捕获所有未处理的异常。
     * 不向客户端暴露异常详情。
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public PermResult<Void> handleException(Exception e) {
        log.error("Unexpected exception", e);
        return PermResult.error(GlobalErrorCode.SYSTEM_ERROR.code(), GlobalErrorCode.SYSTEM_ERROR.message());
    }
}
