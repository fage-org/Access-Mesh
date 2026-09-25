package cn.ac.fage.accessmesh.access.engine.query;

import java.util.List;

/**
 * 统一查询请求（T-PERM-082，设计 §2.1）。
 * <p>
 * 内部契约，不直接作为 Controller 的 JSON DTO——普通外部调用不能指定 Roles、
 * PRESERVE、SKIP、任意读取来源或内部原始实体 ID；合法外部业务键由适配层转换。
 * 同次 execute 只有一个租户、主体、调用环境（首版混批约束）。
 * 空 items 合法：零权限 I/O 返回空结果（C01）。
 * </p>
 *
 * @param tenantId 可信租户上下文 id，正数（结构校验）
 * @param subject  评估主体（User/Roles 封闭变体），非空
 * @param context  可信调用上下文，非空
 * @param reads    读取选项，非空
 * @param items    有序项集合，保留原顺序；null 归一为空集合
 */
public record QueryRequest(long tenantId, Subject subject, CallerContext context,
                           ReadOptions reads, List<QueryItem> items) {

    public QueryRequest {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
