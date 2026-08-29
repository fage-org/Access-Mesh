package cn.ac.fage.accessmesh.access.permission.service.domain.impl;

import cn.ac.fage.accessmesh.common.cache.CacheReadToken;
import cn.ac.fage.accessmesh.common.cache.CacheService;
import cn.ac.fage.accessmesh.access.permission.cache.PermCacheCatalog;
import cn.ac.fage.accessmesh.access.permission.constant.PermConstants;
import cn.ac.fage.accessmesh.access.permission.entity.PermissionCondition;
import cn.ac.fage.accessmesh.access.permission.mapper.PermissionConditionMapper;
import cn.ac.fage.accessmesh.access.permission.service.domain.PermissionConditionDomainService;
import cn.ac.fage.accessmesh.access.permission.vo.ConditionEvaluationDetail;
import cn.ac.fage.accessmesh.access.permission.vo.RolePermEntry;
import cn.ac.fage.accessmesh.perm.common.util.ConditionEvalUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 权限条件领域服务实现类
 * <p>
 * 负责权限条件的评估与缓存管理，支持日期范围、时间范围、IP黑白名单等条件类型。
 * 使用统一 CacheService + PermCacheCatalog 管理缓存。
 * </p>
 */
@Service
public class PermissionConditionDomainServiceImpl implements PermissionConditionDomainService {

    private static final Logger log = LoggerFactory.getLogger(PermissionConditionDomainServiceImpl.class);

    /** 明细展示的 CIDR 掩码条数上限（超出以 … 截断） */
    private static final int MAX_MASKED_IP_SHOWN = 3;

    private final PermissionConditionMapper conditionMapper;
    private final ObjectMapper objectMapper;
    private final CacheService cacheService;

    /**
     * 构造函数注入依赖
     *
     * @param conditionMapper 条件数据访问层
     * @param objectMapper    JSON解析器
     * @param cacheService    统一缓存服务
     */
    public PermissionConditionDomainServiceImpl(PermissionConditionMapper conditionMapper,
                                                 ObjectMapper objectMapper,
                                                 CacheService cacheService) {
        this.conditionMapper = conditionMapper;
        this.objectMapper = objectMapper;
        this.cacheService = cacheService;
    }

    /**
     * 评估权限条目的条件
     * <p>
     * 根据条件规则过滤权限条目，返回满足条件的条目列表。
     * 使用本地缓存避免重复评估同一条件
     * </p>
     *
     * @param tenantId 租户ID
     * @param entries  待评估的权限条目列表
     * @param context  评估上下文，包含日期、时间、IP等环境信息
     * @return 满足条件的权限条目列表
     */
    @Override
    public List<RolePermEntry> evaluate(Long tenantId, List<RolePermEntry> entries,
                                                          Map<String, Object> context) {
        Map<Long, Boolean> conditionCache = new HashMap<>();

        return entries.stream()
            .filter(entry -> {
                if (entry.conditionId() == null || !entry.hasCondition()) {
                    return true;
                }
                return conditionCache.computeIfAbsent(entry.conditionId(),
                    id -> evaluateCondition(tenantId, id, context));
            })
            .collect(Collectors.toList());
    }

    /**
     * 逐项评估权限条目条件并返回评估明细（T-PERM-033 explain DTO 扩展）。
     * <p>
     * 与 {@link #evaluate} 共用逐项评估逻辑，不丢弃条目；
     * 条件值按脱敏规则回传（IP 掩码主机段、日期/时间原样）。
     * 条件加载为批量路径（缓存 getBatch → miss 一次批量查库 → putBatch 回填剩余 TTL），
     * 候选条目共享同一条件时同一 ID 只加载一次（禁用/缺失条件不缓存、也不重复穿透）。
     * </p>
     */
    @Override
    public List<ConditionEvaluationDetail> evaluateDetailed(Long tenantId, List<RolePermEntry> entries,
                                                            Map<String, Object> context) {
        Map<String, Object> ctx = context == null ? Map.of() : context;
        Set<Long> conditionIds = entries.stream()
            .filter(entry -> entry.conditionId() != null && entry.hasCondition())
            .map(RolePermEntry::conditionId)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, LoadedRules> rulesById = conditionIds.isEmpty()
            ? Map.of() : loadRulesBatch(tenantId, conditionIds);

        List<ConditionEvaluationDetail> details = new ArrayList<>();
        for (RolePermEntry entry : entries) {
            if (entry.conditionId() == null || !entry.hasCondition()) {
                continue;
            }
            details.add(evaluateOneDetailed(entry, rulesById.get(entry.conditionId()), ctx));
        }
        return details;
    }

    /**
     * 单条挂条件条目的评估明细（rules 为批量加载结果，可能为 null=未收集到的异常形态）
     */
    private ConditionEvaluationDetail evaluateOneDetailed(RolePermEntry entry, LoadedRules rules,
                                                          Map<String, Object> context) {
        if (rules == null || rules.rules() == null) {
            String status = rules != null ? rules.status() : ConditionEvaluationDetail.STATUS_NOT_FOUND;
            return new ConditionEvaluationDetail(entry.conditionId(), entry.permissionId(), entry.roleId(),
                status, null, false, List.of());
        }
        try {
            RulesEvaluation evaluation = evalRules(entry.conditionId(), rules.rules(), context);
            boolean passed = aggregate(evaluation);
            return new ConditionEvaluationDetail(entry.conditionId(), entry.permissionId(), entry.roleId(),
                ConditionEvaluationDetail.STATUS_OK, evaluation.logic(), passed, evaluation.items());
        } catch (Exception e) {
            log.error("CRITICAL: Unexpected error evaluating condition, conditionId: {}", entry.conditionId(), e);
            return new ConditionEvaluationDetail(entry.conditionId(), entry.permissionId(), entry.roleId(),
                ConditionEvaluationDetail.STATUS_INVALID, null, false, List.of());
        }
    }

    /**
     * 批量加载条件规则（缓存优先，miss 后一次批量查库并按剩余 TTL 回填）。
     * <p>
     * 状态语义与单条加载一致：命中缓存视为 OK（禁用/删除条件经写路径 evict 缓存）；
     * miss 时按实体状态区分 NOT_FOUND（不在结果集=不存在/已删/跨租户）、DISABLED（enabled=false）、
     * INVALID（解析失败）；仅 OK 且解析成功的规则回填缓存。
     * </p>
     */
    private Map<Long, LoadedRules> loadRulesBatch(Long tenantId, Set<Long> conditionIds) {
        Map<Long, JsonNode> cached = cacheService.getBatch(PermCacheCatalog.CONDITION_RULES, tenantId, conditionIds);
        Map<Long, LoadedRules> result = new HashMap<>();
        cached.forEach((id, rules) -> result.put(id, new LoadedRules(ConditionEvaluationDetail.STATUS_OK, rules)));

        Set<Long> missed = new LinkedHashSet<>(conditionIds);
        missed.removeAll(cached.keySet());
        if (missed.isEmpty()) {
            return result;
        }
        CacheReadToken<JsonNode> readToken = cacheService.beginRead(PermCacheCatalog.CONDITION_RULES);
        Map<Long, PermissionCondition> byId = conditionMapper.selectValidByIds(tenantId, missed).stream()
            .collect(Collectors.toMap(PermissionCondition::getId, condition -> condition, (a, b) -> a));
        Map<Long, JsonNode> toCache = new LinkedHashMap<>();
        for (Long conditionId : missed) {
            PermissionCondition condition = byId.get(conditionId);
            if (condition == null) {
                result.put(conditionId, new LoadedRules(ConditionEvaluationDetail.STATUS_NOT_FOUND, null));
                continue;
            }
            if (!Boolean.TRUE.equals(condition.getEnabled())) {
                result.put(conditionId, new LoadedRules(ConditionEvaluationDetail.STATUS_DISABLED, null));
                continue;
            }
            try {
                JsonNode rules = objectMapper.readTree(condition.getConditionRules());
                result.put(conditionId, new LoadedRules(ConditionEvaluationDetail.STATUS_OK, rules));
                if (rules != null) {
                    toCache.put(conditionId, rules);
                }
            } catch (Exception e) {
                log.error("CRITICAL: Failed to parse conditionRules JSON, conditionId: {}", conditionId, e);
                result.put(conditionId, new LoadedRules(ConditionEvaluationDetail.STATUS_INVALID, null));
            }
        }
        if (!toCache.isEmpty()) {
            cacheService.putBatch(readToken, tenantId, toCache);
        }
        return result;
    }

    /**
     * 评估单个条件
     * <p>
     * 从缓存或数据库加载条件规则，并根据逻辑类型（AND/OR）评估各项条件。
     * 采用fail-close策略：解析失败或异常时返回false，拒绝权限
     * </p>
     *
     * @param tenantId    租户ID
     * @param conditionId 条件ID
     * @param context     评估上下文
     * @return 条件评估结果，true表示条件满足
     */
    private boolean evaluateCondition(Long tenantId, Long conditionId, Map<String, Object> context) {
        LoadedRules loaded = loadRules(tenantId, conditionId);
        if (!ConditionEvaluationDetail.STATUS_OK.equals(loaded.status()) || loaded.rules() == null) {
            return false;
        }
        try {
            return aggregate(evalRules(conditionId, loaded.rules(), context));
        } catch (Exception e) {
            log.error("CRITICAL: Unexpected error evaluating condition, conditionId: {}", conditionId, e);
            return false;
        }
    }

    /**
     * 加载条件规则（缓存优先，miss 后查 DB 并按剩余 TTL 回填）。
     * <p>
     * 命中缓存视为 OK（禁用/删除条件经写路径 evict 缓存）；miss 时按实体状态区分
     * NOT_FOUND / DISABLED / INVALID（解析失败），供明细评估展示加载状态。
     * </p>
     */
    private LoadedRules loadRules(Long tenantId, Long conditionId) {
        // ① 查缓存（null = miss）
        JsonNode rules = cacheService.get(PermCacheCatalog.CONDITION_RULES, tenantId, conditionId);
        if (rules != null) {
            return new LoadedRules(ConditionEvaluationDetail.STATUS_OK, rules);
        }

        // ② miss 后查 DB（T-ACCESS-008：DB 读取前记录读取起点，回填只写剩余 TTL）
        CacheReadToken<JsonNode> readToken = cacheService.beginRead(PermCacheCatalog.CONDITION_RULES);
        PermissionCondition condition = conditionMapper.selectOneById(conditionId);
        if (condition == null || !tenantId.equals(condition.getTenantId())) {
            return new LoadedRules(ConditionEvaluationDetail.STATUS_NOT_FOUND, null);
        }
        if (!Boolean.TRUE.equals(condition.getEnabled())) {
            return new LoadedRules(ConditionEvaluationDetail.STATUS_DISABLED, null);
        }

        try {
            rules = objectMapper.readTree(condition.getConditionRules());
            // ③ 回填缓存（剩余 TTL）
            if (rules != null) {
                cacheService.put(readToken, tenantId, conditionId, rules);
            }
            return new LoadedRules(ConditionEvaluationDetail.STATUS_OK, rules);
        } catch (Exception e) {
            log.error("CRITICAL: Failed to parse conditionRules JSON, conditionId: {}", conditionId, e);
            return new LoadedRules(ConditionEvaluationDetail.STATUS_INVALID, null);
        }
    }

    /**
     * 逐项评估条件规则，产出脱敏明细
     */
    private RulesEvaluation evalRules(Long conditionId, JsonNode rules, Map<String, Object> context) {
        String logic = rules.has("logic") ? rules.get("logic").asText() : PermConstants.ConditionLogic.AND;
        // T-PERM-017 P2-B：logic 显式声明且非 AND/OR → fail-close 拒绝（防御已通过写入门禁的存量脏数据）
        boolean logicValid = ConditionEvalUtils.VALID_LOGIC.contains(logic);
        if (!logicValid) {
            log.warn("conditionRules.logic 非法 '{}' (允许值: {})，fail-close 拒绝，conditionId: {}",
                logic, ConditionEvalUtils.VALID_LOGIC, conditionId);
        }
        JsonNode items = rules.get("items");
        // items 节点缺失/非数组与空数组语义不同（旧实现：前者恒拒绝、后者 AND 视为无条件满足）
        boolean itemsArrayPresent = items != null && items.isArray();
        if (!itemsArrayPresent) {
            return new RulesEvaluation(logic, logicValid, false, List.of());
        }
        List<ConditionEvaluationDetail.ItemDetail> details = new ArrayList<>();
        for (JsonNode item : items) {
            String type = item != null && item.has("type") ? item.get("type").asText() : "";
            boolean matched = evaluateItem(item, context);
            details.add(new ConditionEvaluationDetail.ItemDetail(type, maskParams(type, item), matched));
        }
        return new RulesEvaluation(logic, logicValid, true, details);
    }

    /**
     * 按 logic（AND/OR）聚合逐项结果，与重构前运行时判定逐分支一致：
     * 非法 logic 拒绝；items 节点缺失/非数组拒绝；空 items 数组维持旧语义
     * （AND=无条件满足放行、OR=无可满足项拒绝）。
     */
    private boolean aggregate(RulesEvaluation evaluation) {
        if (!evaluation.logicValid() || !evaluation.itemsArrayPresent()) {
            return false;
        }
        boolean allMatch = PermConstants.ConditionLogic.AND.equals(evaluation.logic());
        for (ConditionEvaluationDetail.ItemDetail item : evaluation.items()) {
            if (allMatch && !item.matched()) return false;
            if (!allMatch && item.matched()) return true;
        }
        return allMatch;
    }

    /**
     * 条件参数脱敏摘要（T-PERM-033）：
     * IP 黑白名单掩码主机段（保留前两段与前缀长度，IPv6/非常规整体 MASKED）；
     * 日期/时间为非敏感值原样回传；未知类型整体 MASKED。
     */
    private String maskParams(String type, JsonNode item) {
        JsonNode params = item == null ? null : item.get("params");
        if (params == null || params.isNull()) {
            return null;
        }
        if (PermConstants.ConditionType.IP_WHITELIST.equals(type)
            || PermConstants.ConditionType.IP_BLACKLIST.equals(type)) {
            JsonNode cidrs = params.get("cidrs");
            if (cidrs == null || !cidrs.isArray() || cidrs.isEmpty()) {
                return null;
            }
            List<String> masked = new ArrayList<>();
            for (JsonNode cidr : cidrs) {
                masked.add(maskCidr(cidr.asText()));
            }
            String shown = String.join(", ", masked.size() > MAX_MASKED_IP_SHOWN
                ? masked.subList(0, MAX_MASKED_IP_SHOWN) : masked);
            return masked.size() > MAX_MASKED_IP_SHOWN ? shown + ", …" : shown;
        }
        if (PermConstants.ConditionType.DATE_RANGE.equals(type)
            || PermConstants.ConditionType.TIME_RANGE.equals(type)) {
            String start = params.has("start") ? params.get("start").asText() : null;
            String end = params.has("end") ? params.get("end").asText() : null;
            return start == null || end == null ? null : start + "~" + end;
        }
        return "MASKED";
    }

    /**
     * IPv4 CIDR 掩码：保留前两段与前缀长度（如 192.168.1.0/24 → 192.168.*.*\/24）；
     * IPv6（含 IPv4-mapped 形态）与非常规形式（八位组非数字，如主机名样串）整体 MASKED
     */
    private String maskCidr(String cidr) {
        if (cidr == null) {
            return "MASKED";
        }
        String[] parts = cidr.split("/", 2);
        // IPv4-mapped IPv6（::ffff:192.168.1.0）按点分段也是 4 段，需先排除含冒号形态
        if (parts[0].indexOf(':') >= 0) {
            return "MASKED";
        }
        String[] octets = parts[0].split("\\.");
        if (octets.length == 4 && isValidOctets(octets)) {
            return octets[0] + "." + octets[1] + ".*.*" + (parts.length == 2 ? "/" + parts[1] : "");
        }
        return "MASKED";
    }

    /**
     * IPv4 八位组合法性：每段 1-3 位纯数字
     */
    private boolean isValidOctets(String[] octets) {
        for (String octet : octets) {
            if (octet.isEmpty() || octet.length() > 3
                || !octet.chars().allMatch(Character::isDigit)) {
                return false;
            }
        }
        return true;
    }

    /** 规则加载结果（状态 + 规则树，非 OK 状态时规则为 null） */
    private record LoadedRules(String status, JsonNode rules) {}

    /** 逐项评估中间结果（logic + 合法性 + items 节点形态 + 脱敏明细） */
    private record RulesEvaluation(String logic, boolean logicValid, boolean itemsArrayPresent,
                                   List<ConditionEvaluationDetail.ItemDetail> items) {}

    /**
     * 评估单个条件项
     * <p>
     * 根据条件类型调用对应的评估方法，支持日期范围、时间范围、IP黑白名单等类型
     * </p>
     *
     * @param item   条件项JSON节点
     * @param context 评估上下文
     * @return 条件项评估结果
     */
    private boolean evaluateItem(JsonNode item, Map<String, Object> context) {
        return ConditionEvalUtils.evalItem(item, context,
            PermConstants.ConditionType.DATE_RANGE,
            PermConstants.ConditionType.TIME_RANGE,
            PermConstants.ConditionType.IP_WHITELIST,
            PermConstants.ConditionType.IP_BLACKLIST);
    }

    /**
     * 使指定条件的缓存失效
     * <p>
     * 当条件更新或删除时调用此方法清除缓存，确保下次评估使用最新数据
     * </p>
     *
     * @param tenantId    租户ID
     * @param conditionId 条件ID
     */
    public void evictConditionCache(Long tenantId, Long conditionId) {
        cacheService.evictAfterCommit(PermCacheCatalog.CONDITION_RULES, tenantId, conditionId);
    }

    /**
     * 批量使条件缓存失效
     * <p>
     * 批量清除多个条件的缓存，用于批量更新或删除场景
     * </p>
     *
     * @param tenantId     租户ID
     * @param conditionIds 条件ID集合
     */
    public void evictConditionCacheBatch(Long tenantId, Set<Long> conditionIds) {
        if (conditionIds == null || conditionIds.isEmpty()) {
            return;
        }
        cacheService.evictBatchAfterCommit(PermCacheCatalog.CONDITION_RULES, tenantId, conditionIds);
    }
}