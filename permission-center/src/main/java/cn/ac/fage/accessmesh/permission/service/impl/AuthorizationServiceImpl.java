package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.permission.constant.PermConstants;
import cn.ac.fage.accessmesh.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.permission.dto.req.ResourceResolveKey;
import cn.ac.fage.accessmesh.permission.dto.req.ResourceResolveRequest;
import cn.ac.fage.accessmesh.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.permission.entity.RoleResourcePermission;
import cn.ac.fage.accessmesh.permission.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.permission.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.permission.service.AuthorizationService;
import cn.ac.fage.accessmesh.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.permission.service.domain.UserRoleDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.impl.PermQueryEngine;
import com.mybatisflex.core.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import cn.ac.fage.accessmesh.permission.entity.table.OperationPermissionTableDef;
import cn.ac.fage.accessmesh.permission.entity.table.RoleResourcePermissionTableDef;

/**
 * 授权服务实现类
 * <p>
 * 实现canGrant授权检查功能。仅用于权限委托（授权传递）场景的校验。
 * 一般权限检查应使用PermQueryEngine，本服务仅处理canGrant验证。
 * 采用批量处理策略避免N+1查询问题。
 * </p>
 */
@Service
public class AuthorizationServiceImpl implements AuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationServiceImpl.class);

    private final TypeResolutionService typeResolutionService;
    private final UserRoleDomainService userRoleDomainService;
    private final OperationPermissionMapper operationPermissionMapper;
    private final RoleResourcePermissionMapper roleResourcePermissionMapper;
    private final PermQueryEngine engine;

    /**
     * 构造函数注入依赖
     *
     * @param typeResolutionService       类型解析服务
     * @param userRoleDomainService       用户角色领域服务
     * @param operationPermissionMapper   操作权限数据访问层
     * @param roleResourcePermissionMapper 角色资源权限数据访问层
     * @param engine                      权限查询引擎
     */
    public AuthorizationServiceImpl(TypeResolutionService typeResolutionService,
                                     UserRoleDomainService userRoleDomainService,
                                     OperationPermissionMapper operationPermissionMapper,
                                     RoleResourcePermissionMapper roleResourcePermissionMapper,
                                     PermQueryEngine engine) {
        this.typeResolutionService = typeResolutionService;
        this.userRoleDomainService = userRoleDomainService;
        this.operationPermissionMapper = operationPermissionMapper;
        this.roleResourcePermissionMapper = roleResourcePermissionMapper;
        this.engine = engine;
    }

    /**
     * 检查是否有权限授予指定权限
     * <p>
     * 检查操作者是否有canGrant权限来授予指定的资源操作权限。
     * 这是权限委托的核心检查方法。
     * </p>
     *
     * @param tenantId        租户ID
     * @param operatorId      操作者ID
     * @param resourceTypeCode 资源类型编码
     * @param resourceCode    资源编码
     * @param operationCode   操作码
     * @param scopeAll        是否全局作用域
     * @param domainCode      业务域编码，可选
     * @return 是否有权限授予
     */
    @Override
    public boolean canGrantPermission(Long tenantId, Long operatorId, String resourceTypeCode,
                                       String resourceCode, String operationCode, boolean scopeAll, String domainCode) {
        Set<GrantCheckKey> keys = Set.of(new GrantCheckKey(resourceTypeCode, resourceCode, operationCode, scopeAll));
        Map<String, GrantCheckResult> results = checkGrantPermissionsBatch(tenantId, operatorId, keys, domainCode);
        String key = buildPermissionKey(new GrantCheckKey(resourceTypeCode, resourceCode, operationCode, scopeAll));
        GrantCheckResult result = results.get(key);
        return result != null && result.canGrant();
    }

    /**
     * 批量检查授权权限
     * <p>
     * 批量检查操作者是否有canGrant权限来授予多个权限。
     * 采用批量处理策略避免N+1查询：
     * 1. 批量解析资源类型值
     * 2. 批量查询操作权限
     * 3. 批量解析资源实体ID
     * 4. 批量查询角色资源权限
     * 5. 构建查找映射并逐个评估
     * </p>
     *
     * @param tenantId   租户ID
     * @param operatorId 操作者ID
     * @param permissions 待检查的权限键集合
     * @param domainCode 业务域编码，可选
     * @return 权限键到检查结果的映射
     */
    @Override
    public Map<String, GrantCheckResult> checkGrantPermissionsBatch(Long tenantId, Long operatorId,
                                                                      Set<GrantCheckKey> permissions, String domainCode) {
        if (tenantId == null || operatorId == null || permissions == null || permissions.isEmpty()) {
            return Map.of();
        }

        Set<Long> operatorRoleIds = userRoleDomainService.resolveEffectiveRoles(tenantId, operatorId, null);
        if (operatorRoleIds.isEmpty()) {
            Map<String, GrantCheckResult> results = new HashMap<>();
            for (GrantCheckKey key : permissions) {
                String permKey = buildPermissionKey(key);
                results.put(permKey, new GrantCheckResult(false, "NO_ROLE"));
            }
            return results;
        }

        // ===== 批量优化策略 =====

        // 1. 收集资源类型编码和操作码
        Set<String> resourceTypeCodes = permissions.stream()
            .map(GrantCheckKey::resourceTypeCode)
            .collect(Collectors.toSet());
        Set<String> operationCodes = permissions.stream()
            .map(GrantCheckKey::operationCode)
            .collect(Collectors.toSet());

        // 2. 批量解析资源类型，避免N+1查询
        Map<String, Integer> rawResourceTypeByCode = typeResolutionService.batchResolveTypeValues(
            tenantId, "resource_type", resourceTypeCodes);
        // 键转大写以保持一致的查找
        Map<String, Integer> resourceTypeByCode = new HashMap<>();
        for (Map.Entry<String, Integer> entry : rawResourceTypeByCode.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                resourceTypeByCode.put(entry.getKey().toUpperCase(), entry.getValue());
            }
        }

        // 3. 批量查询操作权限
        Set<Integer> resourceTypeValues = new HashSet<>(resourceTypeByCode.values());
        List<OperationPermission> allOpPerms = operationPermissionMapper.selectListByQuery(
            QueryWrapper.create()
                .where(OperationPermissionTableDef.OPERATION_PERMISSION.TENANT_ID.eq(tenantId))
                .and(OperationPermissionTableDef.OPERATION_PERMISSION.RESOURCE_TYPE.in(resourceTypeValues))
                .and(OperationPermissionTableDef.OPERATION_PERMISSION.CODE.in(operationCodes.stream().map(String::toUpperCase).collect(Collectors.toSet())))
                .and(OperationPermissionTableDef.OPERATION_PERMISSION.DELETE_FLAG.eq(0))
        );

        Map<String, OperationPermission> opPermByKey = new HashMap<>();
        Map<Long, OperationPermission> opPermById = new HashMap<>();
        for (OperationPermission op : allOpPerms) {
            String key = op.getResourceType() + ":" + op.getCode().toUpperCase();
            opPermByKey.put(key, op);
            opPermById.put(op.getId(), op);
        }

        // 4. 批量解析资源实体ID
        List<ResourceResolveRequest> resourceRequests = permissions.stream()
            .filter(key -> !key.scopeAll() && key.resourceCode() != null && !key.resourceCode().isBlank())
            .map(key -> new ResourceResolveRequest(key.resourceTypeCode(), key.resourceCode(), PermConstants.CodeType.DEFAULT, domainCode))
            .distinct()
            .collect(Collectors.toList());
        Map<ResourceResolveKey, Long> resolvedResourceIds = typeResolutionService.batchResolveResourceIds(tenantId, resourceRequests);

        Map<String, Long> resourceEntityIdByCode = new HashMap<>();
        for (Map.Entry<ResourceResolveKey, Long> entry : resolvedResourceIds.entrySet()) {
            ResourceResolveKey key = entry.getKey();
            resourceEntityIdByCode.put(key.resourceTypeCode().toUpperCase() + ":" + key.resourceCode(), entry.getValue());
        }

        // 5. 批量查询角色资源权限
        Set<Long> opPermIds = allOpPerms.stream()
            .map(OperationPermission::getId)
            .collect(Collectors.toSet());

        List<RoleResourcePermission> allPerms = roleResourcePermissionMapper.selectListByQuery(
            QueryWrapper.create()
                .where(RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION.TENANT_ID.eq(tenantId))
                .and(RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION.ABSTRACT_ROLE_ID.in(operatorRoleIds))
                .and(RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION.RESOURCE_TYPE.in(resourceTypeValues))
                .and(RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION.OPERATION_PERMISSION_ID.in(opPermIds))
                .and(RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0))
        );

        // 6. 构建查找映射
        Map<String, List<RoleResourcePermission>> permsBySpecificResource = new HashMap<>();
        Map<String, List<RoleResourcePermission>> permsByScopeAll = new HashMap<>();

        for (RoleResourcePermission perm : allPerms) {
            OperationPermission op = opPermById.get(perm.getOperationPermissionId());
            if (op == null) continue;

            Integer resType = perm.getResourceType();
            String opCode = op.getCode().toUpperCase();
            String baseKey = resType + ":" + opCode;

            if (Boolean.TRUE.equals(perm.getScopeAll())) {
                permsByScopeAll.computeIfAbsent(baseKey, k -> new ArrayList<>()).add(perm);
            } else if (perm.getResourceEntityId() != null) {
                permsBySpecificResource.computeIfAbsent(baseKey + ":" + perm.getResourceEntityId(), k -> new ArrayList<>()).add(perm);
            }
        }

        // ===== 逐个评估权限 =====
        Map<String, GrantCheckResult> results = new HashMap<>();
        for (GrantCheckKey key : permissions) {
            GrantCheckResult result = evaluateGrantPermission(key, resourceTypeByCode, opPermByKey,
                resourceEntityIdByCode, permsBySpecificResource, permsByScopeAll);
            results.put(buildPermissionKey(key), result);
        }
        return results;
    }

    /**
     * 评估单个权限的授权资格
     * <p>
     * 根据预加载的数据评估操作者是否有canGrant权限。
     * 检查步骤：
     * 1. 验证资源类型有效性
     * 2. 验证操作权限有效性
     * 3. 解析资源实体ID（非scopeAll时）
     * 4. 查找匹配的权限记录
     * 5. 检查canGrant标记
     * </p>
     *
     * @param key                    待检查的权限键
     * @param resourceTypeByCode     资源类型编码到值的映射
     * @param opPermByKey            操作权限查找映射
     * @param resourceEntityIdByCode 资源实体ID查找映射
     * @param permsBySpecificResource 特定资源权限映射
     * @param permsByScopeAll        全局作用域权限映射
     * @return 授权检查结果
     */
    private GrantCheckResult evaluateGrantPermission(GrantCheckKey key,
                                                      Map<String, Integer> resourceTypeByCode,
                                                      Map<String, OperationPermission> opPermByKey,
                                                      Map<String, Long> resourceEntityIdByCode,
                                                      Map<String, List<RoleResourcePermission>> permsBySpecificResource,
                                                      Map<String, List<RoleResourcePermission>> permsByScopeAll) {
        String resTypeCodeUpper = key.resourceTypeCode().toUpperCase();
        String opCodeUpper = key.operationCode().toUpperCase();

        Integer resourceTypeValue = resourceTypeByCode.get(resTypeCodeUpper);
        if (resourceTypeValue == null) {
            return new GrantCheckResult(false, "INVALID_RESOURCE_TYPE");
        }

        String opPermKey = resourceTypeValue + ":" + opCodeUpper;
        OperationPermission opPerm = opPermByKey.get(opPermKey);
        if (opPerm == null) {
            return new GrantCheckResult(false, "INVALID_OPERATION");
        }

        Long resourceEntityId = null;
        if (!key.scopeAll() && key.resourceCode() != null && !key.resourceCode().isBlank()) {
            String resKey = resTypeCodeUpper + ":" + key.resourceCode();
            resourceEntityId = resourceEntityIdByCode.get(resKey);
            if (resourceEntityId == null) {
                return new GrantCheckResult(false, "RESOURCE_NOT_FOUND");
            }
        }

        String baseKey = resourceTypeValue + ":" + opCodeUpper;
        List<RoleResourcePermission> matchingPerms = new ArrayList<>();

        if (key.scopeAll()) {
            List<RoleResourcePermission> scopeAllPerms = permsByScopeAll.getOrDefault(baseKey, List.of());
            matchingPerms.addAll(scopeAllPerms);
        } else {
            String specificKey = baseKey + ":" + resourceEntityId;
            List<RoleResourcePermission> specificPerms = permsBySpecificResource.getOrDefault(specificKey, List.of());
            List<RoleResourcePermission> scopeAllPerms = permsByScopeAll.getOrDefault(baseKey, List.of());
            matchingPerms.addAll(specificPerms);
            matchingPerms.addAll(scopeAllPerms);
        }

        if (matchingPerms.isEmpty()) {
            return new GrantCheckResult(false, "NO_PERMISSION");
        }

        for (RoleResourcePermission perm : matchingPerms) {
            if (Boolean.TRUE.equals(perm.getCanGrant())) {
                if (key.scopeAll() && !Boolean.TRUE.equals(perm.getScopeAll())) {
                    continue;
                }
                return new GrantCheckResult(true, null);
            }
        }

        return new GrantCheckResult(false, "NO_GRANT_RIGHT");
    }

    /**
     * 构建权限键字符串
     * <p>
     * 将GrantCheckKey转换为字符串格式用于结果映射查找。
     * 格式：resourceTypeCode:resourceCode:operationCode:scopeType
     * </p>
     *
     * @param key 权限检查键
     * @return 权限键字符串
     */
    private String buildPermissionKey(GrantCheckKey key) {
        return String.format("%s:%s:%s:%s",
            key.resourceTypeCode(),
            key.resourceCode() == null ? "*" : key.resourceCode(),
            key.operationCode(),
            key.scopeAll() ? "ALL" : "SPECIFIC");
    }
}