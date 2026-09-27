package cn.ac.fage.accessmesh.access.infrastructure;

import cn.ac.fage.accessmesh.access.engine.query.QueryValidationException;
import cn.ac.fage.accessmesh.common.enums.GlobalErrorCode;
import cn.ac.fage.accessmesh.common.model.R;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 新查询契约结构错误的 HTTP 映射（T-PERM-090 外评处置，2026-09-27 用户拍板）。
 * <p>
 * {@code QueryValidationException}（保留键伪造、tenantId 非正数、空白类型/操作等执行前
 * 结构拒绝）此前无具名分支、落全局兜底 500 SYSTEM_ERROR——与契约册示例（context 顶层
 * timestamp）直接冲突。本 advice 以高于 common {@code GlobalExceptionHandler} 的优先级
 * 将其映射为既有 400 VALIDATION_FAILED 信封（common 无本类依赖通道，故挂本服务本地）。
 * 内部编程错误构造出的结构拒绝会同形映射为 400——适配层退化归一口径（T-PERM-089/090）
 * 已收敛外部可触发的形态，残余出现时由 warn 日志定位。
 * </p>
 */
@RestControllerAdvice
@Order(0)
public class QueryValidationExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(QueryValidationExceptionHandler.class);

    @ExceptionHandler(QueryValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleQueryValidation(QueryValidationException e) {
        log.warn("QueryValidationException: {}", e.getMessage());
        return R.fail(GlobalErrorCode.VALIDATION_FAILED.code(), e.getMessage());
    }
}
