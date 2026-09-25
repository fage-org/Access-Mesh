package cn.ac.fage.accessmesh.access.engine.query;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 统一查询结果（T-PERM-082，设计 §3.1）。
 * <p>
 * orderedResults 与输入 items 等长、顺序相同；相同目标不同 key 仍各自返回。
 * executionId/evaluatedAt 为本次运行态标识与固定评估时刻（服务端注入时钟，§2.2）。
 * </p>
 *
 * @param executionId    本次执行标识
 * @param evaluatedAt    固定评估时刻（注入时钟的服务端时刻，父子共用）
 * @param orderedResults 与输入等长同序的项结果；null 归一为空集合
 */
public record QueryResult(String executionId, LocalDateTime evaluatedAt, List<ItemResult> orderedResults) {

    public QueryResult {
        orderedResults = orderedResults == null ? List.of() : List.copyOf(orderedResults);
    }
}
