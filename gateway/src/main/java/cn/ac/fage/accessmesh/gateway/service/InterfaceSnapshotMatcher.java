package cn.ac.fage.accessmesh.gateway.service;

import cn.ac.fage.accessmesh.perm.common.dto.resp.InterfaceSnapshotResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.InterfaceSnapshotResp.ApiPermissionEntry;
import cn.ac.fage.accessmesh.perm.common.util.ConditionEvalUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.AntPathMatcher;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 接口快照本地匹配器（T-PERM-001 / T-PERM-017 C4）
 * <p>
 * 在 Gateway 本地内存中对 InterfaceSnapshot 的 allowedApis 做匹配，避免每请求打 RPC。
 * </p>
 * <p>
 * 匹配语义（OR 合并 + 三态结果）：
 * <ol>
 *   <li>遍历所有路由匹配（serviceCode + httpMethod + pathPattern + scopeAll）的条目：
 *     <ul>
 *       <li><strong>无条件条目</strong>（{@code hasCondition=false}）→ 立即 {@link Decision#ALLOW}（"任一无条件授权放行"原则）</li>
 *       <li><strong>含条件且 conditionRules 内联</strong>（{@code gateway_evaluable=true}）→ 本地用请求 context 重评：
 *           通过则 {@link Decision#ALLOW}，不通过继续看下一条</li>
 *       <li><strong>含条件但 conditionRules 缺失</strong>（{@code gateway_evaluable=false} 或防御过滤拒绝）
 *           → 标记需要 fallback，但仍继续遍历——后续可能有无条件条目兜底</li>
 *     </ul>
 *   </li>
 *   <li>遍历结束未命中 ALLOW：
 *     <ul>
 *       <li>有任一条目需要 fallback → {@link Decision#FALLBACK}（调 check-interface 实时鉴权）</li>
 *       <li>否则 → {@link Decision#DENY}</li>
 *     </ul>
 *   </li>
 * </ol>
 * </p>
 * <p>
 * T-PERM-017 C4 关键变更：
 * <ul>
 *   <li>{@code hasCondition=true} 不再直接放行——必须本地评通过或回退实时鉴权</li>
 *   <li>多条匹配 entry 采用 OR 合并，修 P1-②（多授权折叠误拒绝）</li>
 *   <li>{@code conditionRules} 解析使用调用方进程时钟，跨进程一致性由 NTP 保证（参见 {@link ConditionEvalUtils} 类注释）</li>
 * </ul>
 * </p>
 */
public class InterfaceSnapshotMatcher {

    private static final Logger log = LoggerFactory.getLogger(InterfaceSnapshotMatcher.class);
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    /**
     * 匹配决策枚举。
     */
    public enum Decision {
        /** 任一匹配条目放行（无条件 / 含条件本地评通过）。 */
        ALLOW,
        /** 命中含条件条目但 conditionRules 未下发，需 Gateway 调 check-interface 回退实时鉴权。 */
        FALLBACK,
        /** 无任一条目匹配或全部含条件评估失败。 */
        DENY
    }

    private InterfaceSnapshotMatcher() {}

    /**
     * 判断请求是否在快照允许范围内（OR 合并 + 三态）。
     *
     * @param snapshot     接口快照（null / 空 allowedApis → DENY）
     * @param serviceCode  路由服务编码
     * @param httpMethod   请求 HTTP 方法
     * @param path         请求路径
     * @param clientIp     请求客户端 IP（条件评估用）
     * @return ALLOW / FALLBACK / DENY
     */
    public static Decision match(InterfaceSnapshotResp snapshot, String serviceCode, String httpMethod,
                                  String path, String clientIp) {
        if (snapshot == null || snapshot.allowedApis() == null || snapshot.allowedApis().isEmpty()) {
            return Decision.DENY;
        }

        boolean needFallback = false;
        List<ApiPermissionEntry> entries = snapshot.allowedApis();

        // 评估上下文：仅承载 clientIp（与 permission-center / PermissionClient 保持一致）
        Map<String, Object> evalContext = new HashMap<>();
        if (clientIp != null) {
            evalContext.put("clientIp", clientIp);
        }

        for (ApiPermissionEntry entry : entries) {
            if (!matchesRoute(entry, serviceCode, httpMethod, path)) {
                continue;
            }

            if (!entry.hasCondition()) {
                // 无条件授权命中 → 直接放行
                return Decision.ALLOW;
            }

            // 含条件授权：判断是否可本地评估
            String rulesJson = entry.conditionRules();
            if (rulesJson == null || rulesJson.isBlank()) {
                // gateway_evaluable=false 或防御过滤拒绝 → 标记需 fallback，继续看其他 entry
                needFallback = true;
                continue;
            }

            // 内联规则本地重评
            if (evaluateInlineRules(rulesJson, evalContext, entry.conditionId())) {
                return Decision.ALLOW;
            }
            // 评估不通过：不立即拒绝，继续看其他 entry（OR 语义）
        }

        return needFallback ? Decision.FALLBACK : Decision.DENY;
    }

    /**
     * 路由维度匹配：scopeAll 覆盖 / 精确-通配匹配。
     * 仅判断路由是否落在该 entry 范围内，不涉及条件。
     */
    private static boolean matchesRoute(ApiPermissionEntry entry, String serviceCode, String httpMethod, String path) {
        if (!serviceCode.equals(entry.serviceCode())) return false;
        if (Boolean.TRUE.equals(entry.scopeAll())) return true;
        if (entry.httpMethod() != null && !entry.httpMethod().equalsIgnoreCase(httpMethod)) return false;
        if (entry.pathPattern() == null) return false;
        return PATH_MATCHER.match(entry.pathPattern(), path);
    }

    /**
     * 本地重评内联条件规则（fail-close）。
     * <p>
     * 评估规则与 permission-center {@code PermissionConditionDomainServiceImpl.evaluateCondition} 对齐：
     * 支持 AND/OR 逻辑，使用 {@link ConditionEvalUtils#evalItem} 评估每条 item。
     * 解析或评估异常 → 返回 false（拒绝该条 entry，由 OR 合并的其他 entry 兜底）。
     * </p>
     * <p>
     * 类型常量与 permission-center {@code PermConstants.ConditionType} 保持一致（IP_WHITELIST/
     * IP_BLACKLIST/DATE_RANGE/TIME_RANGE），同样收录于 {@link ConditionEvalUtils#GATEWAY_PUSHABLE_TYPES}。
     * 此处硬编码字符串是因 perm-common 不引入 permission-center 常量包；后续若引入跨模块常量类可统一。
     * </p>
     */
    private static boolean evaluateInlineRules(String rulesJson, Map<String, Object> context, Long conditionId) {
        try {
            JsonNode rules = SHARED_MAPPER.readTree(rulesJson);
            String logic = rules.has("logic") ? rules.get("logic").asText() : LOGIC_AND;
            // T-PERM-017 P2-B：logic 显式声明且非 AND/OR → fail-close 拒绝（防御已通过写入门禁的存量脏数据）
            if (!logic.isEmpty() && !ConditionEvalUtils.VALID_LOGIC.contains(logic)) {
                log.warn("Gateway conditionRules.logic 非法 '{}' (允许值: {})，fail-close 拒绝，conditionId={}",
                    logic, ConditionEvalUtils.VALID_LOGIC, conditionId);
                return false;
            }
            JsonNode items = rules.get("items");
            if (items == null || !items.isArray()) return false;

            boolean isAnd = LOGIC_AND.equals(logic);
            for (JsonNode item : items) {
                boolean matched = ConditionEvalUtils.evalItem(item, context,
                    TYPE_DATE_RANGE, TYPE_TIME_RANGE, TYPE_IP_WHITELIST, TYPE_IP_BLACKLIST);
                if (isAnd && !matched) return false;
                if (!isAnd && matched) return true;
            }
            return isAnd; // AND 全过 → true；OR 走到末尾无 true → false
        } catch (Exception e) {
            log.warn("Gateway 本地评估条件规则失败 conditionId={}: {}", conditionId, e.getMessage());
            return false;
        }
    }

    /** 共享 ObjectMapper（线程安全），用于本地规则解析。 */
    private static final ObjectMapper SHARED_MAPPER = new ObjectMapper();

    private static final String LOGIC_AND = "AND";
    private static final String TYPE_DATE_RANGE = "DATE_RANGE";
    private static final String TYPE_TIME_RANGE = "TIME_RANGE";
    private static final String TYPE_IP_WHITELIST = "IP_WHITELIST";
    private static final String TYPE_IP_BLACKLIST = "IP_BLACKLIST";
}
