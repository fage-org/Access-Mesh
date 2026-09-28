package cn.ac.fage.accessmesh.gateway.service;

import cn.ac.fage.accessmesh.perm.common.dto.resp.AdmissionRequirement;
import cn.ac.fage.accessmesh.perm.common.dto.resp.InterfaceAdmissionSnapshotResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.InterfaceAdmissionSnapshotResp.OperationCandidateEntry;
import cn.ac.fage.accessmesh.perm.common.dto.resp.InterfaceAdmissionSnapshotResp.RouteEntry;
import cn.ac.fage.accessmesh.perm.common.util.ConditionEvalUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.AntPathMatcher;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 操作准入快照本地匹配器（T-ACCESS-059，契约总册 §25.2 网关本地判定序）。
 * <p>
 * 固定判定序：校验模式/版本/时效 → 完整路由匹配与歧义检测 → 取得唯一要求 → 评该要求的
 * 条件分支。存在无条件（非 CONTEXT_DEFERRED）或条件通过分支即 MAY_ENTER（ALLOW）；
 * 无通过分支但有需远端求值候选则回源在线判定（FALLBACK）；其余拒绝（DENY）。
 * 坏条件显式不可用（内联缺失/解析失败＝不可用分支，不转为无条件）。
 * </p>
 * <p>
 * 路由匹配从该服务完整启用路由集取全部命中（完整配置优先，N14/N15）：
 * 多匹配同要求去重为一个要求；多匹配异要求为配置故障（CONFIG_FAULT → 终端 503，
 * 不隐式 OR/AND，不挑较弱规则）；无注册匹配拒绝且 ALL 不放行未注册。
 * 模式/版本/时效校验失败同样按配置故障/不可用处理（N22：新链失败不回落旧 API:ACCESS）。
 * </p>
 * <p>
 * 条件评估与 access-service 在线判定使用同一规则算法（{@link ConditionEvalUtils#evalItem}，
 * clientIp＋本进程时钟——跨进程一致性由部署统一时区保证）；本地投影与在线一致性=N12 验收面。
 * </p>
 */
public final class InterfaceAdmissionMatcher {

    private static final Logger log = LoggerFactory.getLogger(InterfaceAdmissionMatcher.class);
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private static final ObjectMapper SHARED_MAPPER = new ObjectMapper();

    private static final String LOGIC_AND = "AND";
    private static final String TYPE_DATE_RANGE = "DATE_RANGE";
    private static final String TYPE_TIME_RANGE = "TIME_RANGE";
    private static final String TYPE_IP_WHITELIST = "IP_WHITELIST";
    private static final String TYPE_IP_BLACKLIST = "IP_BLACKLIST";

    /** 本地判定结果。 */
    public enum Decision {
        /** 存在无条件或条件通过分支（MAY_ENTER）→ 放行业务层做最终检查。 */
        ALLOW,
        /** 无通过分支但有需远端求值候选 → 回源 interface-admission 在线判定。 */
        FALLBACK,
        /** 要求明确但无准入资格（正常拒绝，终端 403）。 */
        DENY,
        /** 配置故障（路由歧义/未知 schema/时效或模式校验失败）→ 终端 503，不伪装用户无权限。 */
        CONFIG_FAULT
    }

    private InterfaceAdmissionMatcher() {}

    /**
     * 本地准入判定。
     *
     * @param snapshot    操作准入快照（null → 不可用 CONFIG_FAULT，由调用方回源/失败关闭）
     * @param serviceCode 路由服务编码（快照主体回显校验用）
     * @param httpMethod  请求 HTTP 方法
     * @param path        请求路径
     * @param clientIp    客户端 IP（条件评估用）
     * @param now         判定时钟（时效校验与时间条件评估同源）
     * @return ALLOW / FALLBACK / DENY / CONFIG_FAULT
     */
    public static Decision match(InterfaceAdmissionSnapshotResp snapshot, String serviceCode,
                                 String httpMethod, String path, String clientIp, LocalDateTime now) {
        if (snapshot == null) {
            return Decision.CONFIG_FAULT;
        }
        // —— 判定序①：校验模式/版本/时效 ——
        if (snapshot.schemaVersion() != InterfaceAdmissionSnapshotResp.CURRENT_SCHEMA_VERSION) {
            log.warn("Admission snapshot schema version unknown: {} (service={})",
                snapshot.schemaVersion(), serviceCode);
            return Decision.CONFIG_FAULT;
        }
        if (!"OPERATION_ADMISSION".equals(snapshot.authorizationStage())) {
            log.warn("Admission snapshot authorizationStage unexpected: {} (service={})",
                snapshot.authorizationStage(), serviceCode);
            return Decision.CONFIG_FAULT;
        }
        if (snapshot.expiresAt() == null || now == null || !now.isBefore(snapshot.expiresAt())) {
            // 过期快照不得继续本地判定（时效校验；缓存层 TTL 之外的双保险）
            return Decision.CONFIG_FAULT;
        }
        if (!Objects.equals(serviceCode, snapshot.serviceCode())) {
            log.warn("Admission snapshot serviceCode mismatch: {} != {} (route={})",
                snapshot.serviceCode(), serviceCode, serviceCode);
            return Decision.CONFIG_FAULT;
        }

        // —— 判定序②：完整路由匹配与歧义检测（全部命中，不按用户权限挑较弱规则） ——
        List<RouteEntry> routes = snapshot.routes() == null ? List.of() : snapshot.routes();
        Set<AdmissionRequirement> matchedRequirements = new LinkedHashSet<>();
        for (RouteEntry route : routes) {
            if (routeMatches(route, httpMethod, path)) {
                matchedRequirements.add(route.requiredPermission());
            }
        }
        if (matchedRequirements.isEmpty()) {
            // 无注册匹配：拒绝（不能因用户有 ALL 而放行未注册路径）
            return Decision.DENY;
        }
        if (matchedRequirements.size() > 1) {
            // 多匹配异要求：配置故障阻断（20070 等价本地检测，终端 503）
            log.warn("Ambiguous admission requirements for {} {}: {}", httpMethod, path, matchedRequirements);
            return Decision.CONFIG_FAULT;
        }

        // —— 判定序③④：唯一要求 → 评该要求的条件分支 ——
        AdmissionRequirement requirement = matchedRequirements.iterator().next();
        List<OperationCandidateEntry> candidates = snapshot.operationCandidates() == null
            ? List.of() : snapshot.operationCandidates();
        Map<String, Object> evalContext = new HashMap<>();
        if (clientIp != null) {
            evalContext.put("clientIp", clientIp);
        }

        boolean needFallback = false;
        for (OperationCandidateEntry candidate : candidates) {
            if (!requirementMatches(candidate, requirement)) {
                continue;
            }
            if (candidate.conditionId() == null
                && !"CONTEXT_DEFERRED".equals(candidate.candidateKind())) {
                // 无条件主授权候选 → MAY_ENTER（业务层做最终检查）
                return Decision.ALLOW;
            }
            if (candidate.conditionId() == null) {
                // CONTEXT_DEFERRED 无条件分支：恒不可本地评估（父运行时判定归业务/在线）
                needFallback = true;
                continue;
            }
            String rulesJson = candidate.conditionRules();
            if (rulesJson == null || rulesJson.isBlank()) {
                // gatewayEvaluable=false 或内联缺失 → 不可用分支，回源（不转为无条件）
                needFallback = true;
                continue;
            }
            Boolean evaluated = evaluateInlineRules(rulesJson, evalContext, candidate.conditionId());
            if (evaluated == null) {
                // 解析失败＝不可用分支（契约 §25.2：内联缺失或解析失败不转为无条件，回源在线判定）
                needFallback = true;
                continue;
            }
            if (evaluated) {
                return Decision.ALLOW;
            }
            // 评估不通过：不立即拒绝，继续看其他分支（OR 语义，另一分支失败不能覆盖已成立来源）
        }
        return needFallback ? Decision.FALLBACK : Decision.DENY;
    }

    private static boolean routeMatches(RouteEntry route, String httpMethod, String path) {
        if (route == null || route.requiredPermission() == null
            || route.httpMethod() == null || route.pathPattern() == null
            || !route.httpMethod().equalsIgnoreCase(httpMethod)) {
            return false;
        }
        return route.pathPattern().equals(path) || PATH_MATCHER.match(route.pathPattern(), path);
    }

    private static boolean requirementMatches(OperationCandidateEntry candidate, AdmissionRequirement requirement) {
        return candidate != null && requirement != null
            && Objects.equals(candidate.resourceTypeCode(), requirement.resourceTypeCode())
            && Objects.equals(candidate.operationCode(), requirement.operationCode());
    }

    /**
     * 本地重评内联条件规则（fail-close，与 access-service 在线判定同规则算法）。
     *
     * @return true/false＝评估结果（false 不立即拒绝，由 OR 合并的其他分支兜底）；
     *         null＝规则不可用（解析失败）——调用方按不可用分支回源，不转为无条件
     */
    private static Boolean evaluateInlineRules(String rulesJson, Map<String, Object> context, Long conditionId) {
        try {
            JsonNode rules = SHARED_MAPPER.readTree(rulesJson);
            String logic = rules.has("logic") ? rules.get("logic").asText() : LOGIC_AND;
            if (!ConditionEvalUtils.VALID_LOGIC.contains(logic)) {
                log.warn("Admission conditionRules.logic invalid '{}' (allowed: {}), unavailable branch, conditionId={}",
                    logic, ConditionEvalUtils.VALID_LOGIC, conditionId);
                return null;
            }
            JsonNode items = rules.get("items");
            if (items == null || !items.isArray()) return null;

            boolean isAnd = LOGIC_AND.equals(logic);
            for (JsonNode item : items) {
                boolean matched = ConditionEvalUtils.evalItem(item, context,
                    TYPE_DATE_RANGE, TYPE_TIME_RANGE, TYPE_IP_WHITELIST, TYPE_IP_BLACKLIST);
                if (isAnd && !matched) return false;
                if (!isAnd && matched) return true;
            }
            return isAnd;
        } catch (Exception e) {
            log.warn("Gateway local admission rule evaluation failed conditionId={}: {}",
                conditionId, e.getMessage());
            return null;
        }
    }
}
