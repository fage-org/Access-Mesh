package cn.ac.fage.accessmesh.access.permission.dto.query;

import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 统一权限批量查询输入类（T-PERM-061 A+ 形态：分组 + 请求级共享装载）。
 * <p>
 * 批量判定（batch-check 族）的引擎入参——item 粒度参数与
 * {@link PermQuery#forAuthCheck} 对齐（targetMode 由 resourceCode 推导：null = TYPE_LEVEL、
 * 有值 = INSTANCE），parentResource 为请求级共享字段（不进分组键，全部 item 共用）。
 * 引擎内部按分组键 {@code (targetMode, resourceTypeCode, operationCode, codeType, domainCode,
 * inheritMode)} 归组后做请求级共享装载与分段评估；items 顺序即原始输入序（结果按下标对齐）。
 * </p>
 * <p>
 * {@link #evalContext} 须为<b>唯一且非空</b>的 {@link PermEvalContext}（evaluatedAt 已钉住，
 * a2 定案：请求级单一评估时刻，全部 item 评估、父判定递归、条件评估共用同一实例）。
 * </p>
 */
@Setter
public class PermBatchQuery {

    private final Long tenantId;

    /**
     * 用户ID（入口封装层解析为角色集合；引擎核心管线只消费 roleIds）
     */
    private Long userId;

    /**
     * 角色ID集合（可选，直接给定角色级查询；优先于 userId 解析）
     */
    private java.util.Set<Long> roleIds;

    /**
     * 条件评估上下文（请求级唯一、evaluatedAt 已钉住——a2 定案）
     */
    private PermEvalContext evalContext;

    // ── 主资源上下文（请求级共享，不进分组键；depend_on 子权限过滤的父判定入参） ──

    private String parentResourceTypeCode;
    private String parentResourceCode;
    private String parentCodeType;
    private java.util.Set<String> parentOperationCodes;

    /**
     * 批量检查项（不可变；顺序即原始输入序）
     */
    private final List<Item> items;

    private PermBatchQuery(Long tenantId, List<Item> items) {
        this.tenantId = Objects.requireNonNull(tenantId);
        this.items = List.copyOf(items);
    }

    /**
     * 创建批量判定查询（/auth/batch-check 族，运行时面）。
     * <p>
     * item 评估口径与 forAuthCheck 一致：条件评估开、条目互斥开、判定面继承按 item 的
     * inheritMode 显式开（缺省关）。parentResource/evalContext 经 setter 补充。
     * </p>
     *
     * @param tenantId 租户ID
     * @param userId   用户ID
     * @param items    批量检查项（顺序即原始输入序）
     * @return 批量查询实例
     */
    public static PermBatchQuery forAuthCheckBatch(Long tenantId, Long userId, List<Item> items) {
        PermBatchQuery q = new PermBatchQuery(tenantId, items);
        q.userId = userId;
        return q;
    }

    /**
     * 设置主资源上下文（请求级共享——全部 item 共用，与 query-scopes/batch-check 契约对齐）。
     */
    public void setParentResource(String resourceTypeCode, String resourceCode,
                                  String codeType, java.util.Set<String> operationCodes) {
        this.parentResourceTypeCode = resourceTypeCode;
        this.parentResourceCode = resourceCode;
        this.parentCodeType = codeType;
        this.parentOperationCodes = operationCodes;
    }

    public Long tenantId() { return tenantId; }
    public Long userId() { return userId; }
    public java.util.Set<Long> roleIds() { return roleIds; }
    public PermEvalContext evalContext() { return evalContext; }
    public String parentResourceTypeCode() { return parentResourceTypeCode; }
    public String parentResourceCode() { return parentResourceCode; }
    public String parentCodeType() { return parentCodeType; }
    public java.util.Set<String> parentOperationCodes() { return parentOperationCodes; }
    public List<Item> items() { return items; }

    /**
     * 批量检查项（forAuthCheck 参数粒度的批量化）。
     *
     * @param resourceTypeCode 资源类型编码
     * @param resourceCode     资源编码，null 表示类型级检查（该 item 归 TYPE_LEVEL 组）
     * @param operationCode    操作编码
     * @param codeType         编码类型（编码目标的解析键组成部分）
     * @param domainCode       业务域编码（解析期参与 ResourceResolveRequest）
     * @param inheritClosure   判定面继承（inheritMode=PARENT/BOTH 显式开；缺省关——
     *                         false 档闭包集恒 {自身}，不得消费祖先闭包映射）
     */
    public record Item(String resourceTypeCode, String resourceCode, String operationCode,
                       String codeType, String domainCode, boolean inheritClosure) {}
}
