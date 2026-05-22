package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.permission.dto.query.PermQuery;
import cn.ac.fage.accessmesh.permission.dto.query.PermResult;
import cn.ac.fage.accessmesh.permission.dto.req.AuthCheckReq;
import cn.ac.fage.accessmesh.permission.dto.req.BatchAuthCheckReq;
import cn.ac.fage.accessmesh.permission.dto.req.CheckInterfaceReq;
import cn.ac.fage.accessmesh.permission.dto.resp.AuthCheckResp;
import cn.ac.fage.accessmesh.permission.dto.resp.BatchAuthCheckResp;
import cn.ac.fage.accessmesh.permission.dto.resp.CheckInterfaceResp;
import cn.ac.fage.accessmesh.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionCheckDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.permission.util.PermResultUtils;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 权限校验领域服务实现类
 * <p>
 * 领域层权限校验适配器，委托PermQueryEngine执行权限查询。
 * 提供单次校验、批量校验和内部校验接口。
 * 将请求参数转换为PermQuery对象，调用PermQueryEngine执行查询，
 * 并将PermResult转换为响应对象返回。
 * 该服务位于领域层，不处理HTTP请求解析等应用层逻辑。
 * </p>
 */
@Service
public class PermissionCheckDomainServiceImpl implements PermissionCheckDomainService {

    private final TypeResolutionService typeResolutionService;
    private final ResourceEntityMapper resourceEntityMapper;
    private final PermQueryEngine engine;

    /**
     * 构造函数注入依赖
     *
     * @param typeResolutionService 类型解析服务
     * @param resourceEntityMapper   资源实体数据访问层
     * @param engine                 权限查询引擎
     */
    public PermissionCheckDomainServiceImpl(TypeResolutionService typeResolutionService,
                                             ResourceEntityMapper resourceEntityMapper,
                                             PermQueryEngine engine) {
        this.typeResolutionService = typeResolutionService;
        this.resourceEntityMapper = resourceEntityMapper;
        this.engine = engine;
    }

    /**
     * 单次权限校验
     * <p>
     * 检查用户对指定资源是否有指定操作的权限。
     * 通过类型解析服务将外部标识转换为内部ID，
     * 构建PermQuery对象并委托PermQueryEngine执行查询。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      权限校验请求，包含用户标识、资源编码、操作码等
     * @return 权限校验响应，包含是否允许、拒绝原因等信息
     */
    @Override
    public AuthCheckResp check(Long tenantId, AuthCheckReq req) {
        Long userId = typeResolutionService.resolveUserId(tenantId, req.subjectTypeCode(), req.subjectExternalId());
        if (userId == null) return AuthCheckResp.deny("USER_NOT_FOUND");
        PermQuery q = PermQuery.forAuthCheck(tenantId, userId,
            req.resourceTypeCode(), req.resourceCode(), req.operationCode());
        q.setCodeType(req.codeType());
        q.setInheritMode(req.inheritMode());
        q.setContext(req.context());
        return PermResultUtils.toAuthCheckResp(engine.query(q));
    }

    /**
     * 批量权限校验
     * <p>
     * 批量检查用户对多个资源的权限。
     * 对每个资源分别构建PermQuery并查询，结果按资源编码或类型组织。
     * 如果用户不存在，所有项都返回拒绝。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      批量权限校验请求，包含用户标识和多个校验项
     * @return 批量权限校验响应，包含每个项的校验结果
     */
    @Override
    public BatchAuthCheckResp batchCheck(Long tenantId, BatchAuthCheckReq req) {
        Long userId = typeResolutionService.resolveUserId(tenantId, req.subjectTypeCode(), req.subjectExternalId());
        if (userId == null) {
            return new BatchAuthCheckResp(req.items().stream()
                .map(item -> new BatchAuthCheckResp.AuthCheckItemResult(
                    item.resourceTypeCode(), item.resourceCode(), item.operationCode(), false, "USER_NOT_FOUND",
                    java.util.List.of(), java.util.List.of()))
                .toList());
        }
        var resultsByKey = new java.util.LinkedHashMap<String, PermResult>();
        for (var item : req.items()) {
            PermQuery q = PermQuery.forAuthCheck(tenantId, userId,
                item.resourceTypeCode(), item.resourceCode(), item.operationCode());
            q.setContext(req.context());
            String key = item.resourceCode() != null && !item.resourceCode().isBlank()
                ? item.resourceCode() : item.resourceTypeCode() + ":" + item.operationCode();
            resultsByKey.put(key, engine.query(q));
        }
        return PermResultUtils.toBatchAuthCheckResp(resultsByKey);
    }

    /**
     * 接口权限校验
     * <p>
     * 校验用户是否有访问特定API接口的权限。
     * 该方法在领域层未实现，应由应用层通过checkInterface方法处理。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      接口校验请求
     * @return 接口校验响应（当前返回拒绝，表示未在领域层实现）
     */
    @Override
    public CheckInterfaceResp checkInterface(Long tenantId, CheckInterfaceReq req) {
        return CheckInterfaceResp.deny("NOT_IMPLEMENTED_IN_DOMAIN_LAYER");
    }

    /**
     * 内部权限校验
     * <p>
     * 使用内部ID（而非外部编码）进行权限校验。
     * 用于领域层内部调用，跳过类型解析步骤。
     * 支持设置资源实体ID集合、操作权限ID集合等参数。
     * queryScopeAll设置为true表示查询所有可见范围，
     * earlyReturnOnScopeAll设置为true表示一旦找到有效权限即返回。
     * </p>
     *
     * @param tenantId              租户ID
     * @param userId                用户ID
     * @param resourceEntityId      资源实体ID
     * @param operationPermissionId 操作权限ID
     * @param inheritMode           继承模式
     * @param context               上下文参数
     * @return 权限校验响应
     */
    @Override
    public AuthCheckResp checkInternal(Long tenantId, Long userId, Long resourceEntityId,
                                        Long operationPermissionId,
                                        String inheritMode, Map<String, Object> context) {
        // 从资源实体ID解析资源类型编码
        String resourceTypeCode = null;
        if (resourceEntityId != null) {
            ResourceEntity resource = resourceEntityMapper.selectValidById(tenantId, resourceEntityId);
            if (resource != null && resource.getResourceType() != null) {
                resourceTypeCode = typeResolutionService.resolveTypeCode(tenantId, "resource_type", resource.getResourceType());
            }
        }
        if (resourceTypeCode == null) {
            return AuthCheckResp.deny("CANNOT_RESOLVE_RESOURCE_TYPE");
        }

        PermQuery q = PermQuery.forAuthCheck(tenantId, userId, resourceTypeCode, null, null);
        q.setOperationPermissionIds(java.util.Set.of(operationPermissionId));
        q.setResourceEntityIds(java.util.Set.of(resourceEntityId));
        q.setInheritMode(inheritMode);
        q.setContext(context);
        q.setQueryScopeAll(true);
        q.setEarlyReturnOnScopeAll(true);
        return PermResultUtils.toAuthCheckResp(engine.query(q));
    }
}