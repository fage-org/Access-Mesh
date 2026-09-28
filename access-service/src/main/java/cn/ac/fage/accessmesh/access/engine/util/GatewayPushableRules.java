package cn.ac.fage.accessmesh.access.engine.util;

import cn.ac.fage.accessmesh.access.rule.entity.PermissionCondition;
import cn.ac.fage.accessmesh.access.rule.mapper.PermissionConditionMapper;
import cn.ac.fage.accessmesh.perm.common.util.ConditionEvalUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 可下发网关的条件规则装载（T-PERM-017 C3 抽取共享，T-ACCESS-059 双装配器复用）。
 * <p>
 * 仅返回 {@code gateway_evaluable=true} 且通过 {@link ConditionEvalUtils#isGatewayPushable}
 * 防御性校验的条件——旧接口快照与操作准入快照共用同一内联判据（缺失/解析失败＝不可用
 * 分支回源，不转为无条件）。防御性校验拒绝场景（绕过写入门禁的脏数据）落 WARN 便于运维修正。
 * </p>
 */
public final class GatewayPushableRules {

    private static final Logger log = LoggerFactory.getLogger(GatewayPushableRules.class);

    private GatewayPushableRules() {}

    /**
     * 批量装载可下发 Gateway 的条件规则。
     *
     * @return conditionId → conditionRules JSON 映射；不可下发的不入 Map（调用方落 null 走回源）
     */
    public static Map<Long, String> loadPushableRules(PermissionConditionMapper conditionMapper,
                                                      ObjectMapper objectMapper,
                                                      Long tenantId, Set<Long> conditionIds) {
        if (conditionIds.isEmpty()) {
            return Map.of();
        }
        java.util.List<PermissionCondition> conditions = conditionMapper.selectValidByIds(tenantId, conditionIds);
        if (conditions.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> pushable = new HashMap<>();
        for (PermissionCondition c : conditions) {
            if (!Boolean.TRUE.equals(c.getGatewayEvaluable())) {
                continue; // 不可下发：Gateway 走回源
            }
            String rules = c.getConditionRules();
            if (rules == null || rules.isBlank()) {
                continue;
            }
            // 防御性校验：即使 DB 误存 gateway_evaluable=true，类型不在白名单也拒绝内联
            try {
                JsonNode tree = objectMapper.readTree(rules);
                if (!ConditionEvalUtils.isGatewayPushable(tree)) {
                    log.warn("条件 [{}] gateway_evaluable=true 但规则含不可下发类型，"
                        + "防御性过滤拒绝内联，将走回源。请检查 DB 数据完整性", c.getId());
                    continue;
                }
                pushable.put(c.getId(), rules);
            } catch (Exception e) {
                log.warn("条件 [{}] 规则 JSON 解析失败，防御性过滤拒绝内联: {}", c.getId(), e.getMessage());
            }
        }
        return pushable;
    }

    /** 条件行是否可本地评估（gateway_evaluable=true）；规则原文可下发性见 {@link #loadPushableRules}。 */
    public static boolean isGatewayEvaluable(PermissionCondition condition) {
        return condition != null && Objects.equals(Boolean.TRUE, condition.getGatewayEvaluable());
    }
}
