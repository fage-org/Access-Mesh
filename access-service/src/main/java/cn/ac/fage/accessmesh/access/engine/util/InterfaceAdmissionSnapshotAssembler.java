package cn.ac.fage.accessmesh.access.engine.util;

import cn.ac.fage.accessmesh.access.engine.query.GrantFact;
import cn.ac.fage.accessmesh.access.engine.query.GrantSetResult;
import cn.ac.fage.accessmesh.access.infrastructure.enums.AccessErrorCode;
import cn.ac.fage.accessmesh.access.resource.entity.ResourceApiMapping;
import cn.ac.fage.accessmesh.access.rule.entity.PermissionCondition;
import cn.ac.fage.accessmesh.access.rule.mapper.PermissionConditionMapper;
import cn.ac.fage.accessmesh.access.type.entity.OperationPermission;
import cn.ac.fage.accessmesh.access.type.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.type.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.access.engine.core.TypeResolutionService;
import cn.ac.fage.accessmesh.perm.common.dto.req.InterfaceAdmissionSnapshotReq;
import cn.ac.fage.accessmesh.perm.common.dto.resp.AdmissionRequirement;
import cn.ac.fage.accessmesh.perm.common.dto.resp.InterfaceAdmissionSnapshotResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.InterfaceAdmissionSnapshotResp.OperationCandidateEntry;
import cn.ac.fage.accessmesh.perm.common.dto.resp.InterfaceAdmissionSnapshotResp.RouteEntry;
import cn.ac.fage.accessmesh.common.exception.BizException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 操作准入快照装配器（T-ACCESS-059，契约总册 §25.2 / 设计 §8.4）。
 * <p>
 * 路由要求解析（操作行批量装载 + 类型值反查，一次批量）、候选投影（原始 GrantFact
 * 不去重合并，按 type-operation＋条件身份＋候选类别归并）与最终组装。条件候选内联
 * 判据与旧接口快照共用 {@link GatewayPushableRules}（gateway_evaluable=true 且四类型
 * 白名单内才内联；缺失/解析失败＝不可用分支回源，不转为无条件）。
 * </p>
 */
@Component
public class InterfaceAdmissionSnapshotAssembler {

    private final OperationPermissionMapper operationPermissionMapper;
    private final PermissionConditionMapper conditionMapper;
    private final TypeResolutionService typeResolutionService;
    private final ObjectMapper objectMapper;

    public InterfaceAdmissionSnapshotAssembler(OperationPermissionMapper operationPermissionMapper,
                                               PermissionConditionMapper conditionMapper,
                                               TypeResolutionService typeResolutionService,
                                               ObjectMapper objectMapper) {
        this.operationPermissionMapper = operationPermissionMapper;
        this.conditionMapper = conditionMapper;
        this.typeResolutionService = typeResolutionService;
        this.objectMapper = objectMapper;
    }

    /** 路由与解析后要求的配对。 */
    public record RouteRequirement(String httpMethod, String pathPattern, AdmissionRequirement requirement) {}

    /**
     * 批量解析映射集的准入要求：required_operation_id 悬空、操作行缺失/软删、
     * 所属类型不可反查均为配置故障 20071（不猜默认操作）；要求类型为 API 同样 20071
     * ——API 授权已随 T-ACCESS-062 全灭，API 类型操作作准入要求恒无候选（死配置，
     * 写侧共用保存入口已拒绝，此处兜底存量/直写脏数据，2026-09-28 拍板收窄）。
     */
    public List<RouteRequirement> resolveRouteRequirements(Long tenantId, List<ResourceApiMapping> mappings) {
        if (mappings.isEmpty()) {
            return List.of();
        }
        Set<Long> operationIds = mappings.stream()
            .map(ResourceApiMapping::getRequiredOperationId).collect(Collectors.toCollection(LinkedHashSet::new));
        if (operationIds.contains(null)) {
            throw configFault("启用映射缺业务操作引用（required_operation_id 悬空）");
        }
        Map<Long, OperationPermission> operationById = operationPermissionMapper.selectValidByIds(tenantId, operationIds)
            .stream().collect(Collectors.toMap(OperationPermission::getId, op -> op));
        Map<Integer, String> typeCodeByValue = reverseTypeValues(tenantId,
            operationById.values().stream().map(OperationPermission::getResourceType).collect(Collectors.toSet()));

        List<RouteRequirement> routes = new ArrayList<>(mappings.size());
        for (ResourceApiMapping mapping : mappings) {
            OperationPermission op = operationById.get(mapping.getRequiredOperationId());
            if (op == null || op.getCode() == null || op.getCode().isBlank()) {
                throw configFault("映射操作引用损坏: " + mapping.getHttpMethod() + " " + mapping.getPathPattern());
            }
            String typeCode = typeCodeByValue.get(op.getResourceType());
            if (typeCode == null) {
                throw configFault("映射操作引用的类型定义不可解析: " + mapping.getHttpMethod()
                    + " " + mapping.getPathPattern());
            }
            if (ResourceTypeCode.API.equals(typeCode)) {
                throw configFault("映射操作引用为 API 类型（API 仅用于接口登记，作准入要求恒无候选）: "
                    + mapping.getHttpMethod() + " " + mapping.getPathPattern());
            }
            routes.add(new RouteRequirement(mapping.getHttpMethod(), mapping.getPathPattern(),
                new AdmissionRequirement(typeCode, op.getCode())));
        }
        return routes;
    }

    /**
     * 候选投影：每个去重要求消费其 admissionFacts 的保留事实，按
     * （条件身份｜无条件）× 候选类别归并；条件可本地评估性与规则内联共用
     * {@link GatewayPushableRules}。CONTEXT_DEFERRED 分支恒 gatewayEvaluable=false
     * （网关不做父运行时判定，业务必须传真实父上下文）。
     */
    public List<OperationCandidateEntry> projectCandidates(Long tenantId,
                                                           Collection<AdmissionRequirement> requirements,
                                                           Map<String, GrantSetResult> factsByRequirement) {
        List<GrantFact> facts = new ArrayList<>();
        Map<AdmissionRequirement, GrantSetResult> resultByRequirement = new LinkedHashMap<>();
        for (AdmissionRequirement requirement : requirements) {
            GrantSetResult grantSet = factsByRequirement.get(requirementKey(requirement));
            if (grantSet == null) {
                continue;
            }
            resultByRequirement.put(requirement, grantSet);
            grantSet.details().stageFacts().forEach(stage -> facts.addAll(stage.retainedAfterEvaluation()));
        }
        if (facts.isEmpty()) {
            return List.of();
        }

        Set<Long> conditionIds = facts.stream()
            .map(GrantFact::conditionId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, PermissionCondition> conditionById = conditionIds.isEmpty() ? Map.of()
            : conditionMapper.selectValidByIds(tenantId, conditionIds).stream()
                .collect(Collectors.toMap(PermissionCondition::getId, c -> c));
        Map<Long, String> pushableRules = GatewayPushableRules.loadPushableRules(
            conditionMapper, objectMapper, tenantId, conditionIds);

        // 归并键：type-operation + 条件身份（null 占位）+ 候选类别（设计 §8.4 归并口径）
        Map<String, OperationCandidateEntry> merged = new LinkedHashMap<>();
        for (Map.Entry<AdmissionRequirement, GrantSetResult> entry : resultByRequirement.entrySet()) {
            AdmissionRequirement requirement = entry.getKey();
            for (GrantFact fact : entry.getValue().details().stageFacts().stream()
                .flatMap(stage -> stage.retainedAfterEvaluation().stream()).toList()) {
                GrantFact.AdmissionCandidateKind kind = fact.admissionCandidateKind();
                boolean hasCondition = fact.hasCondition() && fact.conditionId() != null;
                boolean gatewayEvaluable = kind != GrantFact.AdmissionCandidateKind.CONTEXT_DEFERRED
                    && (!hasCondition || GatewayPushableRules.isGatewayEvaluable(conditionById.get(fact.conditionId())));
                String rules = hasCondition && gatewayEvaluable
                    ? pushableRules.get(fact.conditionId()) : null;
                String key = requirementKey(requirement) + "|" + (hasCondition ? fact.conditionId() : "-")
                    + "|" + kind;
                merged.putIfAbsent(key, new OperationCandidateEntry(
                    requirement.resourceTypeCode(),
                    requirement.operationCode(),
                    hasCondition ? fact.conditionId() : null,
                    gatewayEvaluable,
                    kind.name(),
                    rules));
            }
        }
        return List.copyOf(merged.values());
    }

    /** 组装最终快照（模式/代次/时效字段由调用方给定；恒 OPERATION_ADMISSION＋finalCheckRequired）。 */
    public InterfaceAdmissionSnapshotResp assemble(Long tenantId, InterfaceAdmissionSnapshotReq req,
                                                   long configGeneration, LocalDateTime generatedAt,
                                                   LocalDateTime expiresAt, List<RouteRequirement> routes,
                                                   List<OperationCandidateEntry> candidates) {
        List<RouteEntry> routeEntries = routes.stream()
            .map(route -> new RouteEntry(route.httpMethod(), route.pathPattern(), route.requirement()))
            .toList();
        return new InterfaceAdmissionSnapshotResp(
            InterfaceAdmissionSnapshotResp.CURRENT_SCHEMA_VERSION,
            tenantId,
            new InterfaceAdmissionSnapshotResp.Subject(req.subjectTypeCode(), req.subjectExternalId()),
            req.serviceCode(),
            generatedAt,
            expiresAt,
            configGeneration,
            routeEntries,
            candidates,
            "OPERATION_ADMISSION",
            true);
    }

    /** 类型值 → 类型码反查（一次批量；resource_type 字典）。 */
    private Map<Integer, String> reverseTypeValues(Long tenantId, Set<Integer> typeValues) {
        if (typeValues.isEmpty()) {
            return Map.of();
        }
        return typeResolutionService.batchResolveTypeCodes(tenantId, "resource_type", typeValues);
    }

    private static String requirementKey(AdmissionRequirement requirement) {
        return requirement.resourceTypeCode() + ":" + requirement.operationCode();
    }

    private BizException configFault(String message) {
        return new BizException(AccessErrorCode.ADMISSION_CONFIG_FAULT.getCode(), message);
    }
}
