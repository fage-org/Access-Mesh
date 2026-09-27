package cn.ac.fage.accessmesh.access.infrastructure;

import cn.ac.fage.accessmesh.access.engine.query.CallerContext;
import cn.ac.fage.accessmesh.access.engine.query.QueryValidationException;
import cn.ac.fage.accessmesh.common.enums.GlobalErrorCode;
import cn.ac.fage.accessmesh.common.model.R;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 新查询契约结构错误的 HTTP 映射回归锁（T-PERM-090 外评处置，2026-09-27 用户拍板）。
 * <p>
 * 保留键结构拒绝此前落全局兜底 500（与契约册示例冲突）；本锁钉死
 * advice 将其映射为既有 400 VALIDATION_FAILED 信封——修复前（无具名分支）
 * 本用例的映射断言不可表达（异常直达兜底）。
 * </p>
 */
class QueryValidationExceptionHandlerTest {

    private final QueryValidationExceptionHandler handler = new QueryValidationExceptionHandler();

    @Test
    void shouldMapStructuralRejectionToValidationFailedEnvelope() {
        // 真实触发路径：context 顶层保留键由 CallerContext 构造拒绝（经适配层 fromCallerMap）
        QueryValidationException thrown = assertThrows(QueryValidationException.class,
            () -> new CallerContext(null, java.util.Map.of("timestamp", "2026-04-26T18:00:00")));

        R<Void> resp = handler.handleQueryValidation(thrown);

        assertThat(resp.getCode()).isEqualTo(GlobalErrorCode.VALIDATION_FAILED.code());
        assertThat(resp.getMessage()).contains("timestamp");
    }
}
