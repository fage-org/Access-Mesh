package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.perm.common.util.BusinessKeys;
import cn.ac.fage.accessmesh.perm.common.dto.req.UserEffectivePermissionCodesReq;
import cn.ac.fage.accessmesh.perm.common.dto.resp.UserEffectivePermissionCodesResp;
import cn.ac.fage.accessmesh.access.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.access.permission.dto.query.PermQuery;
import cn.ac.fage.accessmesh.access.permission.dto.query.PermResult;
import cn.ac.fage.accessmesh.access.permission.dto.query.PermViewFilter;
import cn.ac.fage.accessmesh.access.permission.dto.query.PermViewResult;
import cn.ac.fage.accessmesh.access.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.access.permission.service.PermissionViewAppService;
import cn.ac.fage.accessmesh.access.permission.service.domain.SubjectDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.access.permission.util.OperatorContext;
import cn.ac.fage.accessmesh.access.permission.util.PermViewAssembler;
import cn.ac.fage.accessmesh.access.permission.vo.RolePermEntry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class PermissionViewAppServiceImpl implements PermissionViewAppService {

    private final ResourceEntityMapper resourceEntityMapper;
    private final SubjectDomainService subjectDomainService;
    private final TypeResolutionService typeResolutionService;
    private final PermQueryEngine engine;
    private final PermViewAssembler permViewAssembler;

    public PermissionViewAppServiceImpl(ResourceEntityMapper resourceEntityMapper,
                                         SubjectDomainService subjectDomainService,
                                         TypeResolutionService typeResolutionService,
                                         PermQueryEngine engine,
                                         PermViewAssembler permViewAssembler) {
        this.resourceEntityMapper = resourceEntityMapper;
        this.subjectDomainService = subjectDomainService;
        this.typeResolutionService = typeResolutionService;
        this.engine = engine;
        this.permViewAssembler = permViewAssembler;
    }

    /**
     * 用户有效权限码聚合查询（v1.4 双轨并行 / 命名空间统一）。
     * <p>
     * 实现路径：解析 userId 并门禁 USER:VIEW → 解析有效角色集合 →
     * 调 {@link PermQueryEngine#query(PermQuery)} 走 forUserView 管线 →
     * 用 {@link PermViewAssembler#assemble} 应用资源类型白名单 + 排除 API + 不分页 →
     * 遍历引擎返回的 effective 操作投影，拼成
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
        if (!Objects.equals(operatorId, userId)
            && !engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.USER, String.valueOf(userId), OperationCodeConstants.VIEW)) {
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
            .map(RolePermEntry::resourceType)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Integer, String> typeCodeMap = resourceTypeValues.isEmpty()
            ? Map.of()
            : typeResolutionService.batchResolveTypeCodes(tenantId, "resource_type", resourceTypeValues);

        Set<String> permCodes = new LinkedHashSet<>();
        for (PermResult.EffectiveOperationEntry entry : viewResult.getEffectiveOperationEntries()) {
            if (entry.resourceType() == null
                || entry.operationCode() == null) {
                continue;
            }
            String resourceTypeCode = typeCodeMap.get(entry.resourceType());
            if (resourceTypeCode == null) {
                continue;
            }
            permCodes.add(BusinessKeys.permissionCode(resourceTypeCode, entry.operationCode()));
        }
        return new UserEffectivePermissionCodesResp(new ArrayList<>(permCodes));
    }

    @Override
    public EffectiveResourceAccess getEffectiveResourceAccess(Long tenantId, UserEffectivePermissionCodesReq req) {
        PermViewResult viewResult = buildEffectiveView(tenantId, req);
        if (viewResult == null) {
            return new EffectiveResourceAccess(Set.of(), Set.of());
        }
        // scopeAll 条目：resourceEntityId 为 null（全范围授权），按资源类型收集
        Set<Integer> allScopeTypes = new LinkedHashSet<>();
        Set<Long> resourceEntityIds = new LinkedHashSet<>();
        for (PermResult.EffectiveOperationEntry entry : viewResult.getEffectiveOperationEntries()) {
            if (entry.resourceType() == null) {
                continue;
            }
            if (Boolean.TRUE.equals(entry.scopeAll())) {
                allScopeTypes.add(entry.resourceType());
            } else if (entry.resourceEntityId() != null) {
                resourceEntityIds.add(entry.resourceEntityId());
            }
        }
        // 判定面继承（读过滤面默认开，Q12 矩阵；T-PERM-057 落位）：对授权实例集做一次
        // 子孙扩展（而非逐目标闭包）——授父分组 VIEW → 子报表/子菜单实例可见
        // （query-engine-unification.md §5 读过滤面继承落位）
        if (!resourceEntityIds.isEmpty()) {
            for (ResourceEntityMapper.DescendantResult pair :
                resourceEntityMapper.selectDescendantIdsBatch(tenantId, resourceEntityIds)) {
                resourceEntityIds.add(pair.getDescendantId());
            }
        }
        return new EffectiveResourceAccess(allScopeTypes, resourceEntityIds);
    }

    /**
     * 构建用户有效权限视图（getEffectivePermissionCodes 与 getEffectiveResourceAccess 的公共管线）。
     * <p>
     * 步骤：解析 userId → 解析有效角色 → forUserView 引擎管线 →
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

        // 2. 解析有效角色
        Set<Long> roleIds = subjectDomainService.resolveEffectiveRoles(tenantId, userId);
        if (roleIds.isEmpty()) {
            return null;
        }

        // 3. 调引擎获取全量结果
        PermQuery query = PermQuery.forUserView(tenantId, userId);
        query.setRoleIds(roleIds);
        PermResult result = engine.query(query);
        if (!result.allowed()) {
            return null;
        }

        // 4. 通过装配器过滤（仅按资源类型白名单 + 排除 API），关键差异：
        //    - 不传 pageNum/pageSize（PermViewAssembler.paginate 注释明说「分页延迟到调用方聚合后执行」，
        //      assemble 总是返回全量已过滤 entries，故此处天然不分页）
        //    - 不需要 sourceRoles（权限码下发无需来源角色）
        PermViewFilter filter = new PermViewFilter();
        filter.setResourceTypes(req.resourceTypeCodes() == null ? null : new LinkedHashSet<>(req.resourceTypeCodes()));
        filter.setExcludeApiResources(true);
        filter.setIncludeScopePermissions(true);
        filter.setIncludeSourceRoles(false);
        filter.setSourceRoleLimit(0);
        // pageNum/pageSize 不影响 entries 内容（只影响聚合后截断），
        // 此处显式设为 1 避免 PermViewAssembler.paginate 走 "<= 0 用默认 20" 分支
        filter.setPageNum(1);
        filter.setPageSize(Integer.MAX_VALUE);

        return permViewAssembler.assemble(tenantId, result, filter);
    }

}
