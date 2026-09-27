package cn.ac.fage.accessmesh.access.engine.service.impl;

import cn.ac.fage.accessmesh.perm.common.util.BusinessKeyUtil;
import cn.ac.fage.accessmesh.perm.common.dto.req.UserEffectivePermissionCodesReq;
import cn.ac.fage.accessmesh.perm.common.dto.resp.UserEffectivePermissionCodesResp;
import cn.ac.fage.accessmesh.access.engine.constant.OperationCode;
import cn.ac.fage.accessmesh.access.engine.query.CallerContext;
import cn.ac.fage.accessmesh.access.engine.query.Evaluation;
import cn.ac.fage.accessmesh.access.engine.query.FactDetail;
import cn.ac.fage.accessmesh.access.engine.query.GrantFact;
import cn.ac.fage.accessmesh.access.engine.query.GrantSetResult;
import cn.ac.fage.accessmesh.access.engine.query.OutputSpec;
import cn.ac.fage.accessmesh.access.engine.query.PresentationExpansion;
import cn.ac.fage.accessmesh.access.engine.query.QueryExecutionEngine;
import cn.ac.fage.accessmesh.access.engine.query.QueryItem;
import cn.ac.fage.accessmesh.access.engine.query.QueryRequest;
import cn.ac.fage.accessmesh.access.engine.query.ReadOptions;
import cn.ac.fage.accessmesh.access.engine.query.User;
import cn.ac.fage.accessmesh.access.engine.dto.PermViewFilter;
import cn.ac.fage.accessmesh.access.engine.dto.PermViewResult;
import cn.ac.fage.accessmesh.access.engine.query.QueryGate;
import cn.ac.fage.accessmesh.access.type.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.resource.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.access.engine.service.PermissionViewAppService;
import cn.ac.fage.accessmesh.access.engine.core.TypeResolutionService;
import cn.ac.fage.accessmesh.access.infrastructure.util.OperatorContext;
import cn.ac.fage.accessmesh.access.engine.util.PermViewAssembler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class PermissionViewAppServiceImpl implements PermissionViewAppService {

    private final ResourceEntityMapper resourceEntityMapper;
    private final TypeResolutionService typeResolutionService;
    private final QueryExecutionEngine queryEngine;
    private final QueryGate queryGate;
    private final PermViewAssembler permViewAssembler;

    public PermissionViewAppServiceImpl(ResourceEntityMapper resourceEntityMapper,
                                         TypeResolutionService typeResolutionService,
                                         QueryExecutionEngine queryEngine,
                                         QueryGate queryGate,
                                         PermViewAssembler permViewAssembler) {
        this.resourceEntityMapper = resourceEntityMapper;
        this.typeResolutionService = typeResolutionService;
        this.queryEngine = queryEngine;
        this.queryGate = queryGate;
        this.permViewAssembler = permViewAssembler;
    }

    /**
     * 用户有效权限码聚合查询（v1.4 双轨并行 / 命名空间统一）。
     * <p>
     * 实现路径：解析 userId 并门禁 USER:VIEW → 新引擎 GRANT_LIST 全量事实＋有效操作投影
     * （T-PERM-091 迁新 execute）→ 用 {@link PermViewAssembler#assemble} 应用资源类型
     * 白名单 + 排除 API + 不分页 → 遍历引擎有效操作投影，拼成
     * {@code "<resourceTypeCode>:<operationCode>"}，写入 LinkedHashSet 去重。
     * <p>
     * 不分页 / 不截断：所有 entries 全量遍历，确保任何用户的所有有效权限码均被返回。
     */
    @Override
    public UserEffectivePermissionCodesResp getEffectivePermissionCodesForManage(Long tenantId, UserEffectivePermissionCodesReq req) {
        Long operatorId = OperatorContext.getOperatorId();

        // 解析目标用户
        Long userId = typeResolutionService.resolveUserId(tenantId, req.subjectTypeCode(), req.subjectExternalId());
        if (userId == null) {
            return new UserEffectivePermissionCodesResp(List.of());
        }

        // 门禁：自查豁免；查他人时操作者需对被查用户有 USER:VIEW，防任意登录用户枚举 ID 越权读取他人权限码。
        // T-PERM-089：查看他人门禁走 QueryGate（新 execute）。
        if (!Objects.equals(operatorId, userId)
            && !queryGate.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.USER, String.valueOf(userId), OperationCode.VIEW)) {
            throw new SecurityException("Permission denied: VIEW on USER:" + userId);
        }
        return getEffectivePermissionCodes(tenantId, req);
    }

    @Override
    public UserEffectivePermissionCodesResp getEffectivePermissionCodes(Long tenantId, UserEffectivePermissionCodesReq req) {
        PermViewResult viewResult = buildEffectiveView(tenantId, req);
        if (viewResult == null) {
            return new UserEffectivePermissionCodesResp(List.of());
        }

        // 从 effective 操作投影全量提取 resourceTypeCode:operationCode
        //    PermViewResult.resourceTypeCodeMap 是 resourceId -> typeCode（实例级 view 用），
        //    本接口需要 resourceType(int) -> typeCode，需要直接调 typeResolutionService 重新解析。
        Set<Integer> resourceTypeValues = viewResult.getEntries().stream()
            .map(GrantFact::resourceType)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Integer, String> typeCodeMap = resourceTypeValues.isEmpty()
            ? Map.of()
            : typeResolutionService.batchResolveTypeCodes(tenantId, "resource_type", resourceTypeValues);

        // 权限码全量聚合（不分页不截断）：有效操作投影含操作覆盖展开（UPDATE 覆盖 VIEW 等，
        // 方向优先口径——derivation 不作筛选条件，覆盖行与原授行都在投影内，设计 §6.4）
        Set<String> permCodes = new LinkedHashSet<>();
        for (var entry : viewResult.getEffectiveOperationEntries()) {
            if (entry.resourceType() == null
                || entry.operationCode() == null) {
                continue;
            }
            String resourceTypeCode = typeCodeMap.get(entry.resourceType());
            if (resourceTypeCode == null) {
                continue;
            }
            permCodes.add(BusinessKeyUtil.permissionCode(resourceTypeCode, entry.operationCode()));
        }
        return new UserEffectivePermissionCodesResp(new ArrayList<>(permCodes));
    }

    @Override
    public EffectiveResourceAccess getEffectiveResourceAccess(Long tenantId, UserEffectivePermissionCodesReq req) {
        PermViewResult viewResult = buildEffectiveView(tenantId, req);
        if (viewResult == null) {
            return EffectiveResourceAccess.empty();
        }
        // 分类源=过滤后保留事实（GrantFact 显式 scopeAll/resourceEntityId，与旧 effective 条目
        // 同源等价）：scopeAll 条目 resourceEntityId 为 null（全范围授权），按资源类型收集；
        // 实例条目按类型分组（instanceIdsByType，不含子孙扩展）供 T-ACCESS-052 类型页菜单准入
        // 与目录实例过滤的「该类型有任一可见实例」判定
        Set<Integer> allScopeTypes = new LinkedHashSet<>();
        Set<Long> resourceEntityIds = new LinkedHashSet<>();
        Map<Integer, Set<Long>> instanceIdsByType = new LinkedHashMap<>();
        for (GrantFact entry : viewResult.getEntries()) {
            if (entry.resourceType() == null) {
                continue;
            }
            if (Boolean.TRUE.equals(entry.scopeAll())) {
                allScopeTypes.add(entry.resourceType());
            } else if (entry.resourceEntityId() != null) {
                resourceEntityIds.add(entry.resourceEntityId());
                instanceIdsByType.computeIfAbsent(entry.resourceType(), _unused -> new LinkedHashSet<>())
                    .add(entry.resourceEntityId());
            }
        }
        // 判定面继承（读过滤面默认开，Q12 矩阵；T-PERM-057 落位）：对授权实例集做一次
        // 子孙扩展（而非逐目标闭包）——授父分组 VIEW → 子报表/子菜单实例可见
        // （engine/implementation.md §3.4 读过滤面继承落位）
        if (!resourceEntityIds.isEmpty()) {
            for (ResourceEntityMapper.DescendantResult pair :
                resourceEntityMapper.selectDescendantIdsBatch(tenantId, resourceEntityIds)) {
                resourceEntityIds.add(pair.getDescendantId());
            }
        }
        return new EffectiveResourceAccess(allScopeTypes, resourceEntityIds, instanceIdsByType);
    }

    /**
     * 构建用户有效权限视图（getEffectivePermissionCodes 与 getEffectiveResourceAccess 的公共管线）。
     * <p>
     * 步骤：解析 userId → 新引擎 GRANT_LIST 全量事实（User 主体内部完成有效角色解析＋
     * 互斥双删，等价旧 resolveJudgementRoleIds 入口，沿 T-PERM-090 interfaceSnapshot 先例）→
     * 装配器按资源类型白名单过滤（排除 API 资源、包含 scope 权限、不分页）。
     * 任一前置步骤失败返回 null（调用方按「无权限」处理）。
     * </p>
     * <p>
     * 本管线不设 {@code USER:VIEW} 门禁：调用方各自负责入口门禁——
     * permission 域独立 HTTP 入口走 {@link #getEffectivePermissionCodesForManage}
     * （自查豁免 + 查他人需 USER:VIEW）；query 包内部调用由其入口 Controller 门禁
     * （自查豁免 + {@code USER:VIEW}）兜底。
     * </p>
     */
    private PermViewResult buildEffectiveView(Long tenantId, UserEffectivePermissionCodesReq req) {
        // 1. 解析 userId
        Long userId = typeResolutionService.resolveUserId(tenantId, req.subjectTypeCode(), req.subjectExternalId());
        if (userId == null) {
            return null;
        }

        // 2. 新引擎 GRANT_LIST 全量事实＋有效操作投影（EVALUATE+ENFORCE 沿旧视图管线运行时面口径；
        //    clientIp 从当前请求装配——旧引擎入口对无上下文查询的自动装配等价物，IP 类条件
        //    按真实请求 IP 评估而非 fail-closed 摘除（T-PERM-091 外评 P1 修复）；
        //    无角色/零授权行/评估清空统一映射 null——与旧 !allowed() 早退等价）
        QueryItem viewItem = QueryItem.grantListFacts("view", null, Evaluation.full(),
            new OutputSpec(FactDetail.KEPT, false, true, true, PresentationExpansion.NONE, Set.of(), false));
        GrantSetResult result = (GrantSetResult) queryEngine.execute(new QueryRequest(tenantId, new User(userId),
            CallerContext.ofCurrentRequest(), ReadOptions.defaults(), List.of(viewItem))).orderedResults().get(0);
        if (result.collectionStatus() != GrantSetResult.CollectionStatus.PRESENT) {
            return null;
        }

        // 3. 通过装配器过滤（仅按资源类型白名单 + 排除 API）。分页键显式给 MAX_VALUE：
        //    PermViewAssembler.paginate「分页延迟到调用方聚合后执行」，assemble 总是返回全量
        //    已过滤 entries，此处天然不分页（权限码全量聚合，验收第 2 条）；不给 pageSize 会
        //    走 "<= 0 用默认 20" 分支。sourceRoles 不需要（权限码下发无需来源角色）
        PermViewFilter filter = new PermViewFilter();
        filter.setResourceTypes(req.resourceTypeCodes() == null ? null : new LinkedHashSet<>(req.resourceTypeCodes()));
        filter.setExcludeApiResources(true);
        filter.setIncludeScopePermissions(true);
        filter.setIncludeSourceRoles(false);
        filter.setSourceRoleLimit(0);
        filter.setPageNum(1);
        filter.setPageSize(Integer.MAX_VALUE);

        return permViewAssembler.assemble(tenantId, result, filter);
    }

}
