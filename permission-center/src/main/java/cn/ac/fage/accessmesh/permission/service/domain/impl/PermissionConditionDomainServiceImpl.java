package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.common.cache.GenericCacheManager;
import cn.ac.fage.accessmesh.permission.constant.PermConstants;
import cn.ac.fage.accessmesh.permission.entity.PermissionCondition;
import cn.ac.fage.accessmesh.permission.mapper.PermissionConditionMapper;
import cn.ac.fage.accessmesh.permission.service.cache.impl.ConditionRulesCacheManager;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionConditionDomainService;
import cn.ac.fage.accessmesh.permission.vo.RolePermSnapshot;
import cn.ac.fage.accessmesh.permission.util.ConditionEvalUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PermissionConditionDomainServiceImpl implements PermissionConditionDomainService {

    private static final Logger log = LoggerFactory.getLogger(PermissionConditionDomainServiceImpl.class);

    private final PermissionConditionMapper conditionMapper;
    private final ObjectMapper objectMapper;
    private final GenericCacheManager<Long, JsonNode> rulesCacheManager;

    public PermissionConditionDomainServiceImpl(PermissionConditionMapper conditionMapper,
                                                 ObjectMapper objectMapper,
                                                 ConditionRulesCacheManager rulesCacheManager) {
        this.conditionMapper = conditionMapper;
        this.objectMapper = objectMapper;
        this.rulesCacheManager = rulesCacheManager;
    }

    @Override
    public List<RolePermSnapshot.RolePermEntry> evaluate(Long tenantId, List<RolePermSnapshot.RolePermEntry> entries,
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
     * 评估条件
     * <p>
     * 问题7：使用闭包/lambda 传递 tenantId 给缓存 loader
     * 问题11：处理 JSON 解析失败的情况
     * </p>
     */
    private boolean evaluateCondition(Long tenantId, Long conditionId, Map<String, Object> context) {
        // 问题7：使用缓存管理器，通过 BiFunction 传递 tenantId
        JsonNode rules = rulesCacheManager.get(tenantId, conditionId, (tid, cid) -> {
            // loader: 从数据库加载条件规则
            PermissionCondition condition = conditionMapper.selectOneById(cid);
            if (condition == null || !Boolean.TRUE.equals(condition.getEnabled())
                || !tid.equals(condition.getTenantId())) {
                return null; // 返回 null 会被缓存为空值
            }

            try {
                return objectMapper.readTree(condition.getConditionRules());
            } catch (Exception e) {
                log.error("CRITICAL: Failed to parse conditionRules JSON, conditionId: {}", cid, e);
                // fail-close: 不缓存失败结果，抛异常阻止缓存
                throw new RuntimeException("Condition rules JSON parse failed for conditionId: " + cid, e);
            }
        });

        // 如果规则为空（条件不存在、禁用或解析失败），返回 false
        if (rules == null) {
            return false;
        }

        try {
            String logic = rules.has("logic") ? rules.get("logic").asText() : PermConstants.ConditionLogic.AND;
            JsonNode items = rules.get("items");
            if (items == null || !items.isArray()) return false;

            boolean allMatch = logic.equals(PermConstants.ConditionLogic.AND);
            for (JsonNode item : items) {
                boolean matched = evaluateItem(item, context);
                if (allMatch && !matched) return false;
                if (!allMatch && matched) return true;
            }
            return allMatch;
        } catch (Exception e) {
            log.error("CRITICAL: Unexpected error evaluating condition, conditionId: {}", conditionId, e);
            // fail-close: 异常时拒绝权限
            return false;
        }
    }

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
     * 当条件更新或删除时调用此方法
     * </p>
     *
     * @param tenantId    租户 ID
     * @param conditionId 条件 ID
     */
    public void evictConditionCache(Long tenantId, Long conditionId) {
        rulesCacheManager.evict(tenantId, conditionId);
    }

    /**
     * 批量使条件缓存失效
     *
     * @param tenantId     租户 ID
     * @param conditionIds 条件 ID 集合
     */
    public void evictConditionCacheBatch(Long tenantId, Set<Long> conditionIds) {
        if (conditionIds == null || conditionIds.isEmpty()) {
            return;
        }
        rulesCacheManager.evictBatch(tenantId, conditionIds);
    }

    /**
     * 获取缓存管理器（供外部调用）
     */
    public GenericCacheManager<Long, JsonNode> getRulesCacheManager() {
        return rulesCacheManager;
    }
}