package cn.ac.fage.accessmesh.permission.service.impl;

import java.util.ArrayList;
import java.util.List;

import cn.ac.fage.accessmesh.permission.dto.req.AuthCheckReq;
import cn.ac.fage.accessmesh.permission.dto.req.BatchAuthCheckReq;
import cn.ac.fage.accessmesh.permission.dto.req.CheckInterfaceReq;
import cn.ac.fage.accessmesh.permission.dto.resp.AuthCheckResp;
import cn.ac.fage.accessmesh.permission.dto.resp.BatchAuthCheckResp;
import cn.ac.fage.accessmesh.permission.dto.resp.BatchAuthCheckResp.AuthCheckItemResult;
import cn.ac.fage.accessmesh.permission.dto.resp.CheckInterfaceResp;
import cn.ac.fage.accessmesh.permission.entity.ResourceApiMapping;
import cn.ac.fage.accessmesh.permission.mapper.ResourceApiMappingMapper;
import cn.ac.fage.accessmesh.permission.service.PermissionCheckAppService;
import cn.ac.fage.accessmesh.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.permission.dto.query.PermQuery;
import cn.ac.fage.accessmesh.permission.dto.query.PermResult;
import cn.ac.fage.accessmesh.permission.util.PermResultUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.AntPathMatcher;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 权限检查应用服务实现
 * <p>
 * 提供纯校验功能：单次校验、批量校验、接口级校验。
 * 使用 PermQueryEngine 作为统一查询入口。
 * </p>
 */
@Service
public class PermissionCheckAppServiceImpl implements PermissionCheckAppService {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final TypeResolutionService typeResolutionService;
    private final PermQueryEngine engine;
    private final ResourceApiMappingMapper apiMappingMapper;

    public PermissionCheckAppServiceImpl(TypeResolutionService typeResolutionService,
                                         PermQueryEngine engine,
                                         ResourceApiMappingMapper apiMappingMapper) {
        this.typeResolutionService = typeResolutionService;
        this.engine = engine;
        this.apiMappingMapper = apiMappingMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public AuthCheckResp check(Long tenantId, AuthCheckReq req) {
        Long userId = typeResolutionService.resolveUserId(tenantId, req.subjectTypeCode(), req.subjectExternalId());
        if (userId == null) return AuthCheckResp.deny("USER_NOT_FOUND");

        PermQuery q = PermQuery.forAuthCheck(tenantId, userId,
            req.resourceTypeCode(), req.resourceCode(), req.operationCode());
        q.setCodeType(req.codeType());
        q.setDomainCode(req.domainCode());
        if (req.inheritMode() != null) q.setInheritMode(req.inheritMode());
        q.setContext(req.context());

        return PermResultUtils.toAuthCheckResp(engine.query(q));
    }

    @Override
    @Transactional(readOnly = true)
    public BatchAuthCheckResp batchCheck(Long tenantId, BatchAuthCheckReq req) {
        Long userId = typeResolutionService.resolveUserId(tenantId, req.subjectTypeCode(), req.subjectExternalId());
        if (userId == null) {
            return new BatchAuthCheckResp(req.items().stream()
                .map(item -> new AuthCheckItemResult(
                    item.resourceTypeCode(), item.resourceCode(), item.operationCode(), false, "USER_NOT_FOUND",
                    List.of(), List.of()))
                .toList());
        }
        List<AuthCheckItemResult> results = new ArrayList<>();
        for (var item : req.items()) {
            PermQuery q = PermQuery.forAuthCheck(tenantId, userId,
                item.resourceTypeCode(), item.resourceCode(), item.operationCode());
            q.setCodeType(item.codeType());
            q.setDomainCode(item.domainCode());
            q.setInheritMode(item.inheritMode());
            q.setContext(req.context());
            PermResult r = engine.query(q);
            results.add(new AuthCheckItemResult(
                item.resourceTypeCode(), item.resourceCode(), item.operationCode(),
                r.allowed(), r.reason(),
                r.matchedRoleIds().stream().toList(),
                r.matchedPermissionIds().stream().toList()));
        }
        return new BatchAuthCheckResp(List.copyOf(results));
    }

    @Override
    @Transactional(readOnly = true)
    public CheckInterfaceResp checkInterface(Long tenantId, CheckInterfaceReq req) {
        Long userId = typeResolutionService.resolveUserId(tenantId, req.subjectTypeCode(), req.subjectExternalId());
        if (userId == null) return CheckInterfaceResp.deny("USER_NOT_FOUND");

        List<ResourceApiMapping> mappings = apiMappingMapper.selectForInterfaceCheck(
            tenantId, req.serviceCode(), req.httpMethod());
        if (mappings.isEmpty()) return CheckInterfaceResp.deny("API_NOT_REGISTERED");

        List<ResourceApiMapping> matched = mappings.stream()
            .filter(m -> pathMatches(m.getPathPattern(), req.path())).toList();
        if (matched.isEmpty()) return CheckInterfaceResp.deny("API_NOT_REGISTERED");

        Set<Long> entityIds = matched.stream()
            .map(ResourceApiMapping::getResourceEntityId).filter(Objects::nonNull).collect(Collectors.toSet());

        PermQuery q = PermQuery.forInterfaceCheck(tenantId, userId, Set.of("API"), entityIds, "ACCESS");
        q.setContext(req.context());
        return PermResultUtils.toCheckInterfaceResp(engine.query(q), 30);
    }

    private boolean pathMatches(String pattern, String path) {
        if (pattern.equals(path)) return true;
        return PATH_MATCHER.match(pattern, path);
    }
}
