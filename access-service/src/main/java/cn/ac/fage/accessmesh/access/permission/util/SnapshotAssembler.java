package cn.ac.fage.accessmesh.access.permission.util;

import cn.ac.fage.accessmesh.perm.common.dto.resp.InterfaceSnapshotResp;
import cn.ac.fage.accessmesh.perm.common.enums.ScopeMode;
import cn.ac.fage.accessmesh.perm.common.util.ConditionEvalUtils;
import cn.ac.fage.accessmesh.access.permission.dto.query.PermResult;
import cn.ac.fage.accessmesh.access.permission.entity.PermissionCondition;
import cn.ac.fage.accessmesh.access.permission.entity.ResourceApiMapping;
import cn.ac.fage.accessmesh.access.permission.mapper.PermissionConditionMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.ResourceApiMappingMapper;
import cn.ac.fage.accessmesh.access.permission.vo.RolePermEntry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 权限快照装配器
 * <p>
 * 将权限查询结果转换为接口权限快照条目列表。
 * 通过引擎获取用户全量权限，按 API 资源类型过滤，查询 API 映射并组装条目。
 * scopeAll 条目不展开为 N 个 API 资源，而是直接作为一个条目传递。
 * </p>
 * <p>
 * T-PERM-017 C3：对 hasCondition=true 的条目按 {@code permission_condition.gateway_evaluable}
 * 决定是否内联 {@code conditionRules} JSON 到快照：
 * <ul>
 *   <li>{@code gateway_evaluable=true} 且通过 {@link ConditionEvalUtils#isGatewayPushable} 防御
 *       校验 → 内联 rules，Gateway 本地用请求 context（clientIp + 本进程时钟）重评</li>
 *   <li>{@code gateway_evaluable=false} 或防御校验拒绝 → conditionRules=null，
 *       Gateway 命中后回退 check-interface 实时鉴权</li>
 * </ul>
 * </p>
 */
@Component
public class SnapshotAssembler {

    private static final Logger log = LoggerFactory.getLogger(SnapshotAssembler.class);

    private final ResourceApiMappingMapper apiMappingMapper;
    private final PermissionConditionMapper conditionMapper;
    private final ObjectMapper objectMapper;

    public SnapshotAssembler(ResourceApiMappingMapper apiMappingMapper,
                             PermissionConditionMapper conditionMapper,
                             ObjectMapper objectMapper) {
        this.apiMappingMapper = apiMappingMapper;
        this.conditionMapper = conditionMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 从 {@link PermResult} 构建接口权限快照条目列表。
     * <p>
     * 提取所有 API 类型资源的权限条目，查询对应的 API 映射，组装为快照条目。
     * scopeAll 条目不展开，直接作为一个条目放入结果（httpMethod 和 pathPattern 为 null）。
     * 实例级条目照常通过 API 映射组装。
     * </p>
     * <p>
     * T-PERM-017 C3：对 hasCondition=true 的条目按 gateway_evaluable + isGatewayPushable
     * 决定内联 conditionRules JSON 或置 null（Gateway 走 fallback）。
     * </p>
     *
     * @param tenantId    租户ID
     * @param result      权限查询结果（应来自 {@code markConditionsOnly=true} 的查询，保留所有条件条目）
     * @param serviceCode 服务编码
     * @param apiType     API 资源类型值
     * @return API 权限快照条目列表
     */
    public List<InterfaceSnapshotResp.ApiPermissionEntry> buildSnapshot(
            Long tenantId, PermResult result, String serviceCode, Integer apiType) {
        List<RolePermEntry> entries = result.allEntries();
        if (apiType == null || entries.isEmpty()) {
            return List.of();
        }

        // 筛选 API 类型的权限条目
        List<RolePermEntry> apiEntries = entries.stream()
            .filter(e -> e.resourceType() != null && e.resourceType().equals(apiType))
            .toList();

        if (apiEntries.isEmpty()) {
            return List.of();
        }

        // T-PERM-017 C3：批量加载用到的条件规则
        Set<Long> conditionIds = apiEntries.stream()
            .map(RolePermEntry::conditionId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Long, String> pushableRulesById = loadPushableRules(tenantId, conditionIds);

        List<InterfaceSnapshotResp.ApiPermissionEntry> snapshotEntries = new ArrayList<>();

        // 1. scopeAll 条目：每条权限授权独立成一个 ApiPermissionEntry
        // T-PERM-017 C4 修 P1-②：同一 serviceCode 下多条 scopeAll 授权不再折叠——
        // 例如"角色A 无条件 scopeAll + 角色B 含条件 scopeAll"应保留两条，Gateway 用 OR 语义合并：
        // 任一分支放行即允许，避免条件评估失败误拒绝持有无条件授权的请求。
        for (RolePermEntry e : apiEntries) {
            if (Boolean.TRUE.equals(e.scopeAll()) && e.resourceEntityId() == null) {
                String rulesJson = e.hasCondition() && e.conditionId() != null
                    ? pushableRulesById.get(e.conditionId())
                    : null;
                snapshotEntries.add(new InterfaceSnapshotResp.ApiPermissionEntry(
                    serviceCode, null, null, e.hasCondition(), e.conditionId(),
                    rulesJson, ScopeMode.ALL));
            }
        }

        // 2. 实例级条目：按 (resourceEntityId, conditionId) 组合展开
        // T-PERM-017 C4 修 P1-②：原实现每个资源用 anyMatch 标 hasCondition + findFirst 取 conditionId，
        // 导致多授权场景被折叠为单条带条件 entry，Gateway 评条件失败就整体拒绝丢失无条件分支。
        // 改为对每个 (resource, conditionId) 组合各产出一条 entry × 每个 API 映射，
        // Gateway Matcher 用 OR 语义合并 → 任一分支放行即允许。
        List<RolePermEntry> instanceEntries = apiEntries.stream()
            .filter(e -> e.resourceEntityId() != null && !Boolean.TRUE.equals(e.scopeAll()))
            .toList();

        if (!instanceEntries.isEmpty()) {
            Set<Long> instanceResourceIds = instanceEntries.stream()
                .map(RolePermEntry::resourceEntityId)
                .collect(Collectors.toSet());

            // 查询 API 映射
            List<ResourceApiMapping> apiMappings = apiMappingMapper.selectForSnapshot(
                tenantId, serviceCode, instanceResourceIds);

            // 按 resourceEntityId 分组 + 组内按 conditionId 去重
            // 同一资源、同一条件配置下多角色合并为一条（对 Gateway 匹配来说重复无区别）；
            // 无条件授权用 0L 占位 key 与含条件授权区分，避免同资源混合场景被折叠。
            Map<Long, Map<Long, RolePermEntry>> permsByResource = new LinkedHashMap<>();
            for (RolePermEntry e : instanceEntries) {
                Long resourceId = e.resourceEntityId();
                Long condKey = e.hasCondition() && e.conditionId() != null ? e.conditionId() : 0L;
                permsByResource
                    .computeIfAbsent(resourceId, k -> new LinkedHashMap<>())
                    .putIfAbsent(condKey, e);
            }

            for (ResourceApiMapping mapping : apiMappings) {
                Map<Long, RolePermEntry> permsForResource = permsByResource.getOrDefault(
                    mapping.getResourceEntityId(), Map.of());
                if (permsForResource.isEmpty()) continue;

                for (RolePermEntry perm : permsForResource.values()) {
                    String rulesJson = perm.hasCondition() && perm.conditionId() != null
                        ? pushableRulesById.get(perm.conditionId())
                        : null;
                    snapshotEntries.add(new InterfaceSnapshotResp.ApiPermissionEntry(
                        mapping.getServiceCode(),
                        mapping.getHttpMethod(),
                        mapping.getPathPattern(),
                        perm.hasCondition(),
                        perm.hasCondition() ? perm.conditionId() : null,
                        rulesJson,
                        ScopeMode.INSTANCE
                    ));
                }
            }
        }

        return snapshotEntries;
    }

    /**
     * 批量加载可下发 Gateway 的条件规则（T-PERM-017 C3）
     * <p>
     * 仅返回 {@code gateway_evaluable=true} 且通过 {@link ConditionEvalUtils#isGatewayPushable}
     * 防御性校验的条件。任一校验不通过则映射中无此 conditionId，由调用方落 null（走 fallback）。
     * </p>
     * <p>
     * 防御性校验拒绝场景（绕过 C2.5 写入门禁的脏数据）：
     * <ul>
     *   <li>DB 直接 SQL 写入 gateway_evaluable=true + 含未知类型规则</li>
     *   <li>历史数据迁移引入的不一致</li>
     * </ul>
     * 拒绝时落 WARN 日志，便于运维发现并人工修正。
     * </p>
     *
     * @param tenantId     租户ID
     * @param conditionIds 待加载的条件ID集合
     * @return conditionId → conditionRules JSON 映射；不可下发的不入 Map
     */
    private Map<Long, String> loadPushableRules(Long tenantId, Set<Long> conditionIds) {
        if (conditionIds.isEmpty()) {
            return Map.of();
        }
        List<PermissionCondition> conditions = conditionMapper.selectValidByIds(tenantId, conditionIds);
        if (conditions.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> pushable = new HashMap<>();
        for (PermissionCondition c : conditions) {
            if (!Boolean.TRUE.equals(c.getGatewayEvaluable())) {
                continue; // 不可下发：Gateway 走 fallback
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
                        + "防御性过滤拒绝内联，将走 fallback。请检查 DB 数据完整性",
                        c.getId());
                    continue;
                }
                pushable.put(c.getId(), rules);
            } catch (Exception e) {
                log.warn("条件 [{}] 规则 JSON 解析失败，防御性过滤拒绝内联: {}", c.getId(), e.getMessage());
            }
        }
        return pushable;
    }
}
