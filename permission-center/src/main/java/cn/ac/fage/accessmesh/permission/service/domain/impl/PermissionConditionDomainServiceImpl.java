package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.common.cache.CacheService;
import cn.ac.fage.accessmesh.permission.cache.PermCacheCatalog;
import cn.ac.fage.accessmesh.permission.constant.PermConstants;
import cn.ac.fage.accessmesh.permission.entity.PermissionCondition;
import cn.ac.fage.accessmesh.permission.mapper.PermissionConditionMapper;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionConditionDomainService;
import cn.ac.fage.accessmesh.permission.vo.RolePermEntry;
import cn.ac.fage.accessmesh.perm.common.util.ConditionEvalUtils;
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
        // ① 查缓存（null = miss）
        JsonNode rules = cacheService.get(PermCacheCatalog.CONDITION_RULES, tenantId, conditionId);

        // ② miss 后查 DB
        if (rules == null) {
            PermissionCondition condition = conditionMapper.selectOneById(conditionId);
            if (condition == null || !Boolean.TRUE.equals(condition.getEnabled())
                || !tenantId.equals(condition.getTenantId())) {
                return false;
            }

            try {
                rules = objectMapper.readTree(condition.getConditionRules());
                // ③ 回填缓存
                if (rules != null) {
                    cacheService.put(PermCacheCatalog.CONDITION_RULES, tenantId, conditionId, rules);
                }
            } catch (Exception e) {
                log.error("CRITICAL: Failed to parse conditionRules JSON, conditionId: {}", conditionId, e);
                return false;
            }
        }

        // 如果规则为空（条件不存在、禁用或解析失败），返回false
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
            return false;
        }
    }

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