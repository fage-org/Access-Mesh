package cn.ac.fage.accessmesh.permquery.domain.service;

import cn.ac.fage.accessmesh.permission.constant.PermConstants;
import cn.ac.fage.accessmesh.permission.entity.PermissionCondition;
import cn.ac.fage.accessmesh.permquery.domain.model.valueobject.GrantedPermission;
import cn.ac.fage.accessmesh.permquery.infrastructure.adapter.EntityBatchLoadAdapter;
import cn.ac.fage.accessmesh.permquery.infrastructure.adapter.PermCacheAdapter;
import cn.ac.fage.accessmesh.permission.util.ConditionEvalUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 条件评估领域服务
 * <p>
 * 负责评估权限条件是否满足。
 * 返回评估结果映射（不过滤权限条目），调用方自行判断。
 * </p>
 */
@Service
public class ConditionEvaluator {

    private static final Logger log = LoggerFactory.getLogger(ConditionEvaluator.class);

    private final PermCacheAdapter permCacheAdapter;
    private final EntityBatchLoadAdapter entityBatchLoadAdapter;
    private final ObjectMapper objectMapper;

    public ConditionEvaluator(PermCacheAdapter permCacheAdapter,
                              EntityBatchLoadAdapter entityBatchLoadAdapter,
                              ObjectMapper objectMapper) {
        this.permCacheAdapter = permCacheAdapter;
        this.entityBatchLoadAdapter = entityBatchLoadAdapter;
        this.objectMapper = objectMapper;
    }

    /**
     * 评估权限条件
     * <p>
     * 对权限条目列表中的条件进行评估，返回权限ID到评估结果的映射。
     * </p>
     *
     * @param tenantId   租户ID
     * @param entries    待评估的权限条目列表
     * @param context    评估上下文（包含条件判断所需的参数）
     * @return 权限ID → 是否满足 映射
     */
    public Map<Long, Boolean> evaluate(Long tenantId, List<GrantedPermission> entries,
                                        Map<String, Object> context) {
        if (tenantId == null || entries == null || entries.isEmpty()) {
            return Collections.emptyMap();
        }

        // 收集所有条件ID
        Set<Long> conditionIds = entries.stream()
            .map(GrantedPermission::conditionId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        if (conditionIds.isEmpty()) {
            return Collections.emptyMap();
        }

        // 加载条件实体
        Map<Long, PermissionCondition> conditions = entityBatchLoadAdapter.batchLoadConditions(tenantId, conditionIds);

        // 缓存已评估的条件ID结果
        Map<Long, Boolean> conditionEvalCache = new HashMap<>();

        // 评估每个权限条目
        Map<Long, Boolean> results = new LinkedHashMap<>();
        for (GrantedPermission entry : entries) {
            if (entry.conditionId() == null) {
                // 无条件的权限条目视为满足
                continue;
            }

            // 使用缓存避免重复评估同一条件
            Boolean met = conditionEvalCache.computeIfAbsent(
                entry.conditionId(),
                id -> evaluateCondition(tenantId, id, conditions.get(id), context)
            );
            results.put(entry.permissionId(), met);
        }

        return results;
    }

    /**
     * 评估单个条件
     *
     * @param tenantId    租户ID
     * @param conditionId 条件ID
     * @param condition   条件实体
     * @param context     评估上下文
     * @return 是否满足
     */
    private boolean evaluateCondition(Long tenantId, Long conditionId, PermissionCondition condition,
                                       Map<String, Object> context) {
        if (condition == null || !Boolean.TRUE.equals(condition.getEnabled())) {
            return false;
        }

        // 尝试从缓存获取已解析的规则JSON
        JsonNode rules = permCacheAdapter.getConditionRule(tenantId, conditionId);

        // miss 后解析 conditionRules
        if (rules == null) {
            try {
                String conditionRules = condition.getConditionRules();
                if (conditionRules == null || conditionRules.isEmpty()) {
                    return true; // 无规则视为满足
                }
                rules = objectMapper.readTree(conditionRules);
                // 回填缓存
                if (rules != null) {
                    permCacheAdapter.putConditionRule(tenantId, conditionId, rules);
                }
            } catch (Exception e) {
                log.error("Failed to parse conditionRules JSON, conditionId: {}", conditionId, e);
                return false;
            }
        }

        if (rules == null) {
            return false;
        }

        // 评估规则
        try {
            String logic = rules.has("logic") ? rules.get("logic").asText() : PermConstants.ConditionLogic.AND;
            JsonNode items = rules.get("items");
            if (items == null || !items.isArray()) {
                return false;
            }

            boolean allMatch = logic.equals(PermConstants.ConditionLogic.AND);
            for (JsonNode item : items) {
                boolean matched = evaluateItem(item, context != null ? context : Collections.emptyMap());
                if (allMatch && !matched) {
                    return false;
                }
                if (!allMatch && matched) {
                    return true;
                }
            }
            return allMatch;
        } catch (Exception e) {
            log.error("Unexpected error evaluating condition, conditionId: {}", conditionId, e);
            return false;
        }
    }

    /**
     * 评估单个条件项
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
     * 批量评估条件规则
     *
     * @param tenantId    租户ID
     * @param conditionIds 条件ID集合
     * @param context     评估上下文
     * @return 条件ID → 是否满足 映射
     */
    public Map<Long, Boolean> evaluateBatch(Long tenantId, Set<Long> conditionIds,
                                             Map<String, Object> context) {
        if (tenantId == null || conditionIds == null || conditionIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, PermissionCondition> conditions = entityBatchLoadAdapter.batchLoadConditions(tenantId, conditionIds);

        Map<Long, Boolean> results = new LinkedHashMap<>();
        for (Map.Entry<Long, PermissionCondition> entry : conditions.entrySet()) {
            Long conditionId = entry.getKey();
            PermissionCondition condition = entry.getValue();
            boolean met = evaluateCondition(tenantId, conditionId, condition, context != null ? context : Collections.emptyMap());
            results.put(conditionId, met);
        }

        return results;
    }

    /**
     * 应用评估结果到权限条目
     * <p>
     * 将评估结果填充到 GrantedPermission 的 conditionMet 字段。
     * </p>
     *
     * @param entries         权限条目列表
     * @param evaluationResults 评估结果映射（permissionId → 是否满足）
     * @return 填充了评估结果的权限条目列表
     */
    public List<GrantedPermission> applyEvaluationResults(List<GrantedPermission> entries,
                                                          Map<Long, Boolean> evaluationResults) {
        if (entries == null || entries.isEmpty()) {
            return Collections.emptyList();
        }
        if (evaluationResults == null || evaluationResults.isEmpty()) {
            return entries;
        }

        return entries.stream()
            .map(entry -> {
                Boolean met = evaluationResults.get(entry.permissionId());
                if (met != null) {
                    return entry.withConditionMet(met);
                }
                return entry;
            })
            .toList();
    }

    /**
     * 获取条件规则缓存
     *
     * @param tenantId    租户ID
     * @param conditionId 条件ID
     * @return 条件规则JSON
     */
    public JsonNode getConditionRuleCache(Long tenantId, Long conditionId) {
        if (tenantId == null || conditionId == null) {
            return null;
        }
        return permCacheAdapter.getConditionRule(tenantId, conditionId);
    }

    /**
     * 批量获取条件规则缓存
     *
     * @param tenantId     租户ID
     * @param conditionIds 条件ID集合
     * @return 条件ID → 规则JSON 映射
     */
    public Map<Long, JsonNode> getConditionRuleCacheBatch(Long tenantId, Set<Long> conditionIds) {
        if (tenantId == null || conditionIds == null || conditionIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return permCacheAdapter.getConditionRulesBatch(tenantId, conditionIds);
    }
}