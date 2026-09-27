package cn.ac.fage.accessmesh.access.engine.query;

import cn.ac.fage.accessmesh.access.infrastructure.util.HttpRequestUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 判定面薄门面（T-PERM-089，设计 §6.5/§9.4）：管理面单点门禁与批量拒绝集合的
 * 唯一公开入口，内部逐项构造 {@link QueryExecutionEngine#execute} 请求。
 * <p>
 * 方法形状沿旧四入口（hasPermissionByCode/hasPermissionByEntityId/getDeniedResourceCodes/
 * getDeniedEntityIds），消费方按 X03 等价迁移；本类仅依赖新执行器——禁止注入权限
 * Mapper、解析角色或调用条件/互斥服务（§9.4 架构约束）。评估口径＝旧 forValidate 拉平：
 * EVALUATE+ENFORCE+DECISION、判定面继承开（SELF_AND_ANCESTORS）、类型级回退放行
 * （TypeFallback.ALLOW，scopeAll 先行）；clientIp 从当前请求自动装配（无请求上下文
 * 时 IP 类条件 fail-closed，沿旧 autoFillEvalContext 语义）。主体必须是权限域投影主体
 * （abstract_user.id）。空输入零引擎调用直接返回。
 * </p>
 */
@Component
public class QueryGate {

    private final QueryExecutionEngine engine;

    public QueryGate(QueryExecutionEngine engine) {
        this.engine = engine;
    }

    /**
     * 按业务编码检查是否有权限（code 传 null = 类型级校验）。
     *
     * @param tenantId         租户ID
     * @param subjectId        权限域投影主体ID（abstract_user.id）
     * @param resourceTypeCode 资源类型码
     * @param resourceCode     业务编码，null 表示类型级校验
     * @param operationCode    操作码
     * @return 是否有权限
     */
    public boolean hasPermissionByCode(Long tenantId, Long subjectId, String resourceTypeCode,
                                        String resourceCode, String operationCode) {
        if (resourceCode == null) {
            return decide(tenantId, subjectId, typeLevel(resourceTypeCode, operationCode));
        }
        return decide(tenantId, subjectId, target(new TargetClause(
            operation(resourceTypeCode, operationCode), new ByCode(resourceCode, null, null))));
    }

    /**
     * 按 resource_entity.id 检查是否有权限（仅引擎内部或已完成解析的调用方）。
     * <p>
     * 实例目标直接以 {@code resource_entity.id} 匹配，不做 code 解析；使用边界沿旧口径
     * （资源树、API 映射等 resource_entity 管理链路），USER/ROLE 等业务对象门禁与跨服务
     * SDK 禁止使用（统一业务编码，见 {@link #hasPermissionByCode}）。
     * </p>
     *
     * @param tenantId         租户ID
     * @param subjectId        权限域投影主体ID（abstract_user.id）
     * @param resourceTypeCode 资源类型码
     * @param resourceEntityId resource_entity.id，null 表示类型级校验
     * @param operationCode    操作码
     * @return 是否有权限
     */
    public boolean hasPermissionByEntityId(Long tenantId, Long subjectId, String resourceTypeCode,
                                            Long resourceEntityId, String operationCode) {
        if (resourceEntityId == null) {
            return decide(tenantId, subjectId, typeLevel(resourceTypeCode, operationCode));
        }
        return decide(tenantId, subjectId, target(new TargetClause(
            operation(resourceTypeCode, operationCode), new ByEntityId(resourceEntityId))));
    }

    /**
     * 批量获取被拒绝的业务编码集合（纯查询不抛异常）。
     * <p>
     * 每个目标独立 DECISION item（PQ-01 修复语义随 T-PERM-095 已为现行行为）；
     * 未知类型/未知操作/未解析到投影实体的编码直接拒绝（fail-closed）；无角色全拒。
     * 输入原键按原序回映射（去重）。
     * </p>
     *
     * @param tenantId         租户ID
     * @param subjectId        权限域投影主体ID（abstract_user.id）
     * @param resourceTypeCode 资源类型码
     * @param resourceCodes    业务编码集合
     * @param operationCode    操作码
     * @return 被拒绝的业务编码集合
     */
    public Set<String> getDeniedResourceCodes(Long tenantId, Long subjectId, String resourceTypeCode,
                                               Set<String> resourceCodes, String operationCode) {
        if (resourceCodes == null || resourceCodes.isEmpty()) {
            return Set.of();
        }
        Map<String, DecisionResult> results = decideAll(tenantId, subjectId,
            new ArrayList<>(new LinkedHashSet<>(resourceCodes)), code -> new TargetClause(
                operation(resourceTypeCode, operationCode), new ByCode(code, null, null)));
        Set<String> denied = new LinkedHashSet<>();
        results.forEach((code, result) -> {
            if (result.outcome() == DecisionResult.Decision.DENY) {
                denied.add(code);
            }
        });
        return denied;
    }

    /**
     * 批量获取被拒绝的 resource_entity.id 集合（纯查询不抛异常，ID 轨）。
     * <p>
     * 按 ID 独立 item 判定后回映射输入 ID（不把 ID 转业务码）；fail-closed 口径同
     * {@link #getDeniedResourceCodes}。
     * </p>
     *
     * @param tenantId         租户ID
     * @param subjectId        权限域投影主体ID（abstract_user.id）
     * @param resourceTypeCode 资源类型码
     * @param resourceEntityIds resource_entity.id 集合
     * @param operationCode    操作码
     * @return 被拒绝的 resource_entity.id 集合
     */
    public Set<Long> getDeniedEntityIds(Long tenantId, Long subjectId, String resourceTypeCode,
                                         Set<Long> resourceEntityIds, String operationCode) {
        if (resourceEntityIds == null || resourceEntityIds.isEmpty()) {
            return Set.of();
        }
        Map<Long, DecisionResult> results = decideAll(tenantId, subjectId,
            new ArrayList<>(new LinkedHashSet<>(resourceEntityIds)), id -> new TargetClause(
                operation(resourceTypeCode, operationCode), new ByEntityId(id)));
        Set<Long> denied = new LinkedHashSet<>();
        results.forEach((id, result) -> {
            if (result.outcome() == DecisionResult.Decision.DENY) {
                denied.add(id);
            }
        });
        return denied;
    }

    private boolean decide(Long tenantId, Long subjectId, Selection selection) {
        QueryItem item = QueryItem.decision("gate", selection, OutputSpec.minimal());
        QueryResult result = engine.execute(request(tenantId, subjectId, List.of(item)));
        return ((DecisionResult) result.orderedResults().get(0)).outcome() == DecisionResult.Decision.ALLOW;
    }

    /** 独立目标逐 item 批量表达（一次 execute，键=targetKey 字面值，去重后天然唯一）。 */
    private <K> Map<K, DecisionResult> decideAll(Long tenantId, Long subjectId,
                                                 List<K> distinctTargets,
                                                 java.util.function.Function<K, TargetClause> clause) {
        List<QueryItem> items = new ArrayList<>();
        Map<String, K> targetsByItemKey = new LinkedHashMap<>();
        for (K targetKey : distinctTargets) {
            String itemKey = String.valueOf(targetKey);
            items.add(QueryItem.decision(itemKey, target(clause.apply(targetKey)), OutputSpec.minimal()));
            targetsByItemKey.put(itemKey, targetKey);
        }
        QueryResult result = engine.execute(request(tenantId, subjectId, items));
        Map<K, DecisionResult> byTarget = new LinkedHashMap<>();
        result.orderedResults().forEach(itemResult -> {
            DecisionResult decision = (DecisionResult) itemResult;
            byTarget.put(targetsByItemKey.get(decision.key()), decision);
        });
        return byTarget;
    }

    private static QueryRequest request(Long tenantId, Long subjectId, List<QueryItem> items) {
        return new QueryRequest(tenantId, new User(subjectId), callerContext(), ReadOptions.defaults(), items);
    }

    private static CallerContext callerContext() {
        return CallerContext.of(HttpRequestUtils.getClientIp(HttpRequestUtils.currentRequest()));
    }

    private static TypeLevel typeLevel(String resourceTypeCode, String operationCode) {
        return new TypeLevel(List.of(operation(resourceTypeCode, operationCode)));
    }

    private static TargetSet target(TargetClause clause) {
        return new TargetSet(List.of(clause), Inheritance.SELF_AND_ANCESTORS, TypeFallback.ALLOW, null);
    }

    private static TypeOperation operation(String resourceTypeCode, String operationCode) {
        return new TypeOperation(resourceTypeCode, operationCode);
    }
}
