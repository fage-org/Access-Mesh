package cn.ac.fage.accessmesh.access.permission.service.domain.impl;

import cn.ac.fage.accessmesh.common.cache.CacheReadToken;
import cn.ac.fage.accessmesh.common.cache.CacheService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.access.infrastructure.PermissionChangeContext;
import cn.ac.fage.accessmesh.access.permission.cache.PermCacheCatalog;
import cn.ac.fage.accessmesh.access.permission.constant.PermConstants;
import cn.ac.fage.accessmesh.access.permission.dto.req.ApplyGrantPlanReq;
import cn.ac.fage.accessmesh.access.permission.entity.PermissionCondition;
import cn.ac.fage.accessmesh.access.permission.enums.ConditionSource;
import cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.access.permission.mapper.PermissionConditionMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.access.permission.service.domain.PermissionConditionDomainService;
import cn.ac.fage.accessmesh.access.permission.util.JsonValidationUtils;
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

    /** 内联条件 code 生成前缀（T-PERM-048：inline- + UUID，与管理页手输 code 空间天然隔离） */
    private static final String INLINE_CODE_PREFIX = "inline-";

    private final PermissionConditionMapper conditionMapper;
    private final RoleResourcePermissionMapper rolePermMapper;
    private final ObjectMapper objectMapper;
    private final CacheService cacheService;

    /**
     * 构造函数注入依赖
     *
     * @param conditionMapper 条件数据访问层
     * @param rolePermMapper  授权数据访问层（内联回收引用归零判定，T-PERM-048）
     * @param objectMapper    JSON解析器
     * @param cacheService    统一缓存服务
     */
    public PermissionConditionDomainServiceImpl(PermissionConditionMapper conditionMapper,
                                                 RoleResourcePermissionMapper rolePermMapper,
                                                 ObjectMapper objectMapper,
                                                 CacheService cacheService) {
        this.conditionMapper = conditionMapper;
        this.rolePermMapper = rolePermMapper;
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
        if (!LoadedRules.STATUS_OK.equals(loaded.status()) || loaded.rules() == null) {
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
            return new LoadedRules(LoadedRules.STATUS_OK, rules);
        }

        // ② miss 后查 DB（T-ACCESS-008：DB 读取前记录读取起点，回填只写剩余 TTL）
        CacheReadToken<JsonNode> readToken = cacheService.beginRead(PermCacheCatalog.CONDITION_RULES);
        PermissionCondition condition = conditionMapper.selectOneById(conditionId);
        if (condition == null || !tenantId.equals(condition.getTenantId())) {
            return new LoadedRules(LoadedRules.STATUS_NOT_FOUND, null);
        }
        if (!Boolean.TRUE.equals(condition.getEnabled())) {
            return new LoadedRules(LoadedRules.STATUS_DISABLED, null);
        }

        try {
            rules = objectMapper.readTree(condition.getConditionRules());
            // ③ 回填缓存（剩余 TTL）
            if (rules != null) {
                cacheService.put(readToken, tenantId, conditionId, rules);
            }
            return new LoadedRules(LoadedRules.STATUS_OK, rules);
        } catch (Exception e) {
            log.error("CRITICAL: Failed to parse conditionRules JSON, conditionId: {}", conditionId, e);
            return new LoadedRules(LoadedRules.STATUS_INVALID, null);
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
        List<ItemDetail> details = new ArrayList<>();
        for (JsonNode item : items) {
            String type = item != null && item.has("type") ? item.get("type").asText() : "";
            boolean matched = evaluateItem(item, context);
            details.add(new ItemDetail(type, maskParams(type, item), matched));
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
        for (ItemDetail item : evaluation.items()) {
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
     * IPv6（含 IPv4-mapped 形态）与非常规形式（八位组非 0-255 数字、前缀非 0-128 数字，
     * 如主机名样串/超范围值/前缀位夹带任意串）整体 MASKED——回显前所有片段均须通过校验
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
        if (octets.length == 4 && isValidOctets(octets)
            && (parts.length == 1 || isValidPrefix(parts[1]))) {
            return octets[0] + "." + octets[1] + ".*.*" + (parts.length == 2 ? "/" + parts[1] : "");
        }
        return "MASKED";
    }

    /**
     * IPv4 八位组合法性：每段 1-3 位纯数字且值在 0-255
     */
    private boolean isValidOctets(String[] octets) {
        for (String octet : octets) {
            if (!isDigitsInRange(octet, 255)) {
                return false;
            }
        }
        return true;
    }

    /**
     * CIDR 前缀合法性：1-3 位纯数字且值在 0-128
     */
    private boolean isValidPrefix(String prefix) {
        return isDigitsInRange(prefix, 128);
    }

    /**
     * 数字片段校验：1-3 位纯数字且值不超过 max（已限 3 位，parseInt 无溢出）
     */
    private boolean isDigitsInRange(String value, int max) {
        if (value.isEmpty() || value.length() > 3
            || !value.chars().allMatch(Character::isDigit)) {
            return false;
        }
        return Integer.parseInt(value) <= max;
    }

    /** 单个条件项评估结果（原 ConditionEvaluationDetail.ItemDetail 迁入，T-PERM-059） */
    private record ItemDetail(String type, String maskedParams, boolean matched) {}

    /** 规则加载结果（状态 + 规则树，非 OK 状态时规则为 null；状态常量原 ConditionEvaluationDetail 迁入，T-PERM-059） */
    private record LoadedRules(String status, JsonNode rules) {
        static final String STATUS_OK = "OK";
        static final String STATUS_DISABLED = "DISABLED";
        static final String STATUS_NOT_FOUND = "NOT_FOUND";
        static final String STATUS_INVALID = "INVALID";
    }

    /** 逐项评估中间结果（logic + 合法性 + items 节点形态 + 脱敏明细） */
    private record RulesEvaluation(String logic, boolean logicValid, boolean itemsArrayPresent,
                                   List<ItemDetail> items) {}

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

    /**
     * 条件规则写入口径校验（T-PERM-048 双轨共享：管理页 create/update 与授权内联轨同源）。
     * <p>
     * ① JSON 语法合法；② gatewayEvaluable=true 时 items[].type 全部在白名单内
     * （T-PERM-017 C2.5，含未知类型默认 fail-close）。
     * </p>
     */
    @Override
    public void assertConditionRulesValid(String conditionRules, boolean gatewayEvaluable) {
        JsonValidationUtils.validateJson(conditionRules);
        if (!gatewayEvaluable) {
            return;
        }
        if (conditionRules == null || conditionRules.isBlank()) {
            throw new BizException(PermissionErrorCode.CONDITION_RULES_INVALID.getCode(),
                "gatewayEvaluable=true 但 conditionRules 为空");
        }
        JsonNode tree;
        try {
            tree = objectMapper.readTree(conditionRules);
        } catch (Exception e) {
            throw new BizException(PermissionErrorCode.CONDITION_RULES_INVALID.getCode(),
                "conditionRules 解析失败: " + e.getMessage());
        }
        if (!ConditionEvalUtils.isGatewayPushable(tree)) {
            throw new BizException(PermissionErrorCode.CONDITION_RULES_INVALID.getCode(),
                "gatewayEvaluable=true 仅允许 logic ∈ "
                    + ConditionEvalUtils.VALID_LOGIC
                    + "（或缺省=AND）且 items[].type ∈ "
                    + ConditionEvalUtils.GATEWAY_PUSHABLE_TYPES + " 的规则");
        }
    }

    @Override
    public PermissionCondition createInlineCondition(Long tenantId, Long operatorId,
        ApplyGrantPlanReq.InlineConditionDef def) {
        boolean gatewayEvaluable = def.gatewayEvaluable() != null && def.gatewayEvaluable();
        // 规则写入口径与管理页轨同源（双轨共享校验）
        assertConditionRulesValid(def.conditionRules(), gatewayEvaluable);
        PermissionCondition condition = new PermissionCondition();
        condition.setTenantId(tenantId);
        // code 自动生成（inline- + UUID，总长 43 ≤ 列宽 64）：租户内碰撞概率可忽略，
        // uk_permission_condition 兜底（随授权计划事务失败，由用户重提交）
        condition.setCode(INLINE_CODE_PREFIX + java.util.UUID.randomUUID());
        condition.setName(def.name());
        condition.setConditionRules(def.conditionRules());
        condition.setEnabled(true);
        condition.setGatewayEvaluable(gatewayEvaluable);
        condition.setSource(ConditionSource.INLINE.getValue());
        condition.setCreatedBy(operatorId);
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        condition.setCreatedAt(now);
        condition.setUpdatedAt(now);
        condition.setDeleteFlag(0L);
        conditionMapper.insert(condition);
        return condition;
    }

    @Override
    public void editInlineCondition(Long tenantId, Long operatorId, PermissionCondition condition,
        ApplyGrantPlanReq.InlineConditionDef def) {
        if (!ConditionSource.INLINE.getValue().equals(condition.getSource())) {
            throw new BizException(PermissionErrorCode.CONDITION_INLINE_NOT_MANAGEABLE.getCode(),
                "仅内联条件可在授权页编辑（管理页条件请在权限条件页更改）: " + condition.getCode());
        }
        boolean gatewayEvaluable = def.gatewayEvaluable() != null ? def.gatewayEvaluable()
            : Boolean.TRUE.equals(condition.getGatewayEvaluable());
        // 最终态联合校验：只切 flag 用 DB 老 rules / 带 rules 用新 rules（管理页轨同口径）
        assertConditionRulesValid(
            def.conditionRules() != null ? def.conditionRules() : condition.getConditionRules(),
            gatewayEvaluable);
        condition.setName(def.name());
        if (def.conditionRules() != null) {
            condition.setConditionRules(def.conditionRules());
        }
        condition.setGatewayEvaluable(gatewayEvaluable);
        condition.setUpdatedBy(operatorId);
        condition.setUpdatedAt(java.time.LocalDateTime.now());
        conditionMapper.update(condition);
        PermissionChangeContext.markConditions(tenantId, Set.of(condition.getId()));
    }

    @Override
    public Set<Long> recycleOrphanInlineConditions(Long tenantId, Set<Long> candidateIds) {
        if (candidateIds == null || candidateIds.isEmpty()) {
            return Set.of();
        }
        List<PermissionCondition> candidates = conditionMapper.selectValidByIds(tenantId, candidateIds);
        Set<Long> inlineIds = candidates.stream()
            .filter(condition -> ConditionSource.INLINE.getValue().equals(condition.getSource()))
            .map(PermissionCondition::getId)
            .collect(Collectors.toSet());
        if (inlineIds.isEmpty()) {
            return Set.of();
        }
        // 引用归零判定：apply 已落库（removes/updates 均已生效）后调用，仍被引用的跳过
        // （conditionCode 引用轨对 INLINE 已 20060 焊死，仍被引用=防御分支）
        Set<Long> stillReferenced = rolePermMapper.selectReferencedConditionIds(tenantId, inlineIds);
        Set<Long> recyclable = inlineIds.stream()
            .filter(id -> !stillReferenced.contains(id))
            .collect(Collectors.toSet());
        if (recyclable.isEmpty()) {
            return Set.of();
        }
        conditionMapper.softDeleteBatch(tenantId, new java.util.ArrayList<>(recyclable),
            java.time.LocalDateTime.now());
        PermissionChangeContext.markConditions(tenantId, recyclable);
        return recyclable;
    }
}