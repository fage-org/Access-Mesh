package cn.ac.fage.accessmesh.access.permission.service.impl;

import java.util.ArrayList;
import java.util.List;

import cn.ac.fage.accessmesh.access.permission.dto.req.AuthCheckReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.BatchAuthCheckReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.CheckInterfaceReq;
import cn.ac.fage.accessmesh.access.permission.dto.resp.AuthCheckResp;
import cn.ac.fage.accessmesh.access.permission.dto.resp.BatchAuthCheckResp;
import cn.ac.fage.accessmesh.access.permission.dto.resp.BatchAuthCheckResp.AuthCheckItemResult;
import cn.ac.fage.accessmesh.access.permission.dto.resp.CheckInterfaceResp;
import cn.ac.fage.accessmesh.access.permission.entity.ResourceApiMapping;
import cn.ac.fage.accessmesh.access.permission.mapper.ResourceApiMappingMapper;
import cn.ac.fage.accessmesh.access.permission.service.PermissionCheckAppService;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.access.permission.dto.query.PermEvalContext;
import cn.ac.fage.accessmesh.access.permission.dto.query.PermQuery;
import cn.ac.fage.accessmesh.access.permission.dto.query.PermResult;
import cn.ac.fage.accessmesh.access.permission.util.PermResultUtils;
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

    /**
     * 构造函数注入依赖
     *
     * @param typeResolutionService 类型解析服务
     * @param engine                 权限查询引擎
     * @param apiMappingMapper       API映射数据访问层
     */
    public PermissionCheckAppServiceImpl(TypeResolutionService typeResolutionService,
                                         PermQueryEngine engine,
                                         ResourceApiMappingMapper apiMappingMapper) {
        this.typeResolutionService = typeResolutionService;
        this.engine = engine;
        this.apiMappingMapper = apiMappingMapper;
    }

    /**
     * 单次权限校验
     * <p>
     * 校验指定用户对某资源的某操作是否有权限。
     * 使用PermQuery.forAuthCheck构建查询，通过引擎返回结果。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      校验请求，包含用户标识、资源类型、资源编码、操作码等
     * @return 校验响应，包含是否允许、拒绝原因、命中结果记录与条件评估状态（T-API-003：matched id 字段族恢复回传）
     */
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
        // T-PERM-058 主资源上下文：depend_on 子权限实例的父判定入参（不传=fail-closed 排除子行）
        if (req.parentResourceTypeCode() != null && req.parentResourceCode() != null) {
            q.setParentResource(req.parentResourceTypeCode(), req.parentResourceCode(),
                req.parentCodeType(), toOperationCodeSet(req.parentOperationCodes()));
        }
        q.setEvalContext(PermEvalContext.fromCallerMap(req.context()));

        return PermResultUtils.toAuthCheckResp(engine.query(q));
    }

    /**
     * 批量权限校验
     * <p>
     * 批量校验用户对多个资源操作的权限。
     * 逐项调用引擎查询，返回每项的校验结果。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      批量校验请求，包含用户标识和校验项列表
     * @return 批量校验响应，包含每项的校验结果列表
     */
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
            // T-PERM-058 主资源上下文（请求级）：与 query-scopes 对齐——批量项共享同一父上下文
            if (req.parentResourceTypeCode() != null && req.parentResourceCode() != null) {
                q.setParentResource(req.parentResourceTypeCode(), req.parentResourceCode(),
                    req.parentCodeType(), toOperationCodeSet(req.parentOperationCodes()));
            }
            q.setEvalContext(PermEvalContext.fromCallerMap(req.context()));
            PermResult r = engine.query(q);
            results.add(new AuthCheckItemResult(
                item.resourceTypeCode(), item.resourceCode(), item.operationCode(),
                r.allowed(), r.reason(),
                r.matchedRoleIds().stream().toList(),
                r.matchedPermissionIds().stream().toList()));
        }
        return new BatchAuthCheckResp(List.copyOf(results));
    }

    /**
     * 接口级权限校验
     * <p>
     * 校验用户是否有权访问指定的API接口。
     * 根据服务编码和HTTP方法查找API映射，匹配路径模式，
     * 然后使用PermQuery.forInterfaceCheck校验ACCESS权限。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      接口校验请求，包含用户标识、服务编码、HTTP方法和请求路径
     * @return 接口校验响应，包含是否允许、拒绝原因与匹配资源详情列表（T-API-003：resourceId 与 matched id 字段族恢复回传）
     */
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
        q.setEvalContext(PermEvalContext.fromCallerMap(req.context()));
        return PermResultUtils.toCheckInterfaceResp(engine.query(q), 30);
    }

    /**
     * 路径匹配检查
     * <p>
     * 检查请求路径是否匹配路径模式。
     * 支持精确匹配和Ant风格模式匹配（如 /api/**）。
     * </p>
     *
     * @param pattern 路径模式
     * @param path    请求路径
     * @return 是否匹配
     */
    /**
     * 主资源操作码集合转换（null 整体透传=父判定不限操作由引擎按必填口径处理；
     * null 元素防御过滤——SDK 请求经 JSON 反序列化可含 null 值，Set.copyOf 禁 null
     * 会把可 400/拒绝的入参放大成 500，PermEvalContext.filterValid 同款缺陷类先例）。
     */
    private static Set<String> toOperationCodeSet(List<String> operationCodes) {
        if (operationCodes == null) {
            return null;
        }
        return Set.copyOf(operationCodes.stream().filter(Objects::nonNull).toList());
    }

    private boolean pathMatches(String pattern, String path) {
        if (pattern.equals(path)) return true;
        return PATH_MATCHER.match(pattern, path);
    }
}
