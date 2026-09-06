package cn.ac.fage.accessmesh.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;

import cn.ac.fage.accessmesh.common.model.R;
import cn.ac.fage.accessmesh.common.enums.GlobalErrorCode;
import jakarta.validation.ConstraintViolationException;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 全局异常处理器
 * <p>
 * 统一处理各类异常，返回标准响应格式。
 * 使用@RestControllerAdvice注解，自动应用于所有Controller。
 * 注意：不向客户端暴露异常堆栈信息，保护系统安全。
 * </p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 业务异常处理
     * <p>
     * 处理BizException异常，返回业务错误码和消息。
     * 业务异常通常由参数校验失败或业务规则限制触发。
     * </p>
     *
     * @param e 业务异常对象
     * @return 包含错误码和消息的标准响应
     */
    @ExceptionHandler(BizException.class)
    public R<Void> handleBizException(BizException e) {
        log.warn("BizException: code={}, message={}", e.getErrorCode(), e.getMessage());
        return R.fail(e.getErrorCode(), e.getMessage());
    }

    /**
     * 系统异常处理
     * <p>
     * 处理SystemException异常，记录完整堆栈信息用于排查问题。
     * 返回通用错误消息，不暴露系统细节。
     * </p>
     *
     * @param e 系统异常对象
     * @return 包含错误码和通用消息的标准响应
     */
    @ExceptionHandler(SystemException.class)
    public R<Void> handleSystemException(SystemException e) {
        log.error("SystemException: code={}, message={}", e.getErrorCode(), e.getMessage(), e);
        return R.fail(e.getErrorCode(), "系统异常");
    }

    /**
     * 安全异常处理
     * <p>
     * 处理SecurityException异常，权限不足或安全违规时触发。
     * 返回403错误码，提示权限不足。
     * </p>
     *
     * @param e 安全异常对象
     * @return 包含403错误码的标准响应
     */
    @ExceptionHandler(SecurityException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public R<Void> handleSecurityException(SecurityException e) {
        log.warn("SecurityException: {}", e.getMessage());
        return R.fail(403, "权限不足");
    }

    /**
     * 参数校验异常处理
     * <p>
     * 处理@Valid注解校验失败抛出的MethodArgumentNotValidException异常。
     * 提取第一个校验错误消息返回给客户端。
     * </p>
     *
     * @param e 参数校验异常对象
     * @return 包含校验错误消息的标准响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        log.warn("Validation failed: {}", msg);
        return R.fail(GlobalErrorCode.VALIDATION_FAILED.code(), msg);
    }

    /**
     * 约束违规异常处理
     * <p>
     * 处理ConstraintViolationException异常，通常由方法参数约束校验失败触发。
     * </p>
     *
     * @param e 约束违规异常对象
     * @return 包含错误消息的标准响应
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleConstraintViolation(ConstraintViolationException e) {
        log.warn("Constraint violation: {}", e.getMessage());
        return R.fail(GlobalErrorCode.VALIDATION_FAILED.code(), e.getMessage());
    }

    /**
     * 非法参数异常处理
     * <p>
     * 处理IllegalArgumentException异常。
     * 不向客户端暴露具体异常消息，返回通用错误提示。
     * 记录请求ID用于问题追踪。
     * </p>
     *
     * @param e       非法参数异常对象
     * @param request HTTP请求对象，用于获取请求ID
     * @return 包含通用错误提示的标准响应
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleIllegalArgumentException(IllegalArgumentException e, HttpServletRequest request) {
        String requestId = request.getHeader("X-Request-Id");
        log.warn("IllegalArgumentException [requestId={}]: {}", requestId, e.getMessage());
        return R.fail(400, "参数错误", requestId);
    }

    /**
     * JSON解析异常处理
     * <p>
     * 处理请求体JSON格式错误导致的HttpMessageNotReadableException异常。
     * 返回请求体格式错误的提示。
     * </p>
     *
     * @param e JSON解析异常对象
     * @return 包含格式错误提示的标准响应
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("HttpMessageNotReadable: {}", e.getMessage());
        return R.fail(GlobalErrorCode.VALIDATION_FAILED.code(), "请求体格式错误");
    }

    /**
     * 通用异常处理
     * <p>
     * 作为兜底处理，捕获所有未被特定处理器处理的异常。
     * 记录完整堆栈信息用于问题排查。
     * 不向客户端暴露异常详情，返回通用系统错误消息。
     * </p>
     *
     * @param e 异常对象
     * @return 包含通用系统错误消息的标准响应
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<Void> handleException(Exception e) {
        log.error("Unexpected exception", e);
        return R.fail(GlobalErrorCode.SYSTEM_ERROR.code(), GlobalErrorCode.SYSTEM_ERROR.message());
    }
}
