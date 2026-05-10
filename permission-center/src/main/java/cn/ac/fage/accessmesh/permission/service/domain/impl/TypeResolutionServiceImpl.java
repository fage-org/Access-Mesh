package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.permission.constant.PermConstants;
import cn.ac.fage.accessmesh.permission.dto.req.ResourceResolveKey;
import cn.ac.fage.accessmesh.permission.dto.req.ResourceResolveRequest;
import cn.ac.fage.accessmesh.permission.entity.AbstractUser;
import cn.ac.fage.accessmesh.permission.entity.BizDomain;
import cn.ac.fage.accessmesh.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.permission.entity.TypeDefinition;
import cn.ac.fage.accessmesh.permission.entity.AbstractRole;
import cn.ac.fage.accessmesh.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.permission.mapper.AbstractUserMapper;
import cn.ac.fage.accessmesh.permission.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.permission.mapper.BizDomainMapper;
import cn.ac.fage.accessmesh.permission.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.permission.mapper.TypeDefinitionMapper;
import cn.ac.fage.accessmesh.permission.service.domain.TypeResolutionService;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import cn.ac.fage.accessmesh.permission.entity.table.TypeDefinitionTableDef;
import cn.ac.fage.accessmesh.permission.entity.table.AbstractUserTableDef;
import cn.ac.fage.accessmesh.permission.entity.table.ResourceEntityTableDef;
import cn.ac.fage.accessmesh.permission.entity.table.BizDomainTableDef;
import cn.ac.fage.accessmesh.permission.entity.table.AbstractRoleTableDef;
import cn.ac.fage.accessmesh.permission.entity.table.OperationPermissionTableDef;

/**
 * 类型解析服务实现类
 * <p>
 * 将外部稳定的业务键解析为内部数据库ID。
 * 支持Redis缓存以提升解析性能。
 * </p>
 */
@Service
public class TypeResolutionServiceImpl implements TypeResolutionService {

    private static final String TYPE_VALUE_CACHE_KEY_PREFIX = "perm:type:value:";
    private static final String TYPE_CODE_CACHE_KEY_PREFIX = "perm:type:code:";
    private static final long TYPE_CACHE_TTL_HOURS = 1; // 字典/枚举配置类数据，L2 TTL为1小时
    private static final long NULL_CACHE_TTL_SECONDS = 30; // NULL值缓存TTL不超过30秒，防止缓存穿透
    private static final String NULL_MARKER = "##NULL##";

    private final TypeDefinitionMapper typeDefinitionMapper;
    private final AbstractUserMapper abstractUserMapper;
    private final ResourceEntityMapper resourceEntityMapper;
    private final BizDomainMapper bizDomainMapper;
    private final AbstractRoleMapper abstractRoleMapper;
    private final OperationPermissionMapper operationPermissionMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 构造函数注入依赖
     * <p>
     * TODO: 构造函数依赖过多(7个)，违反单一职责原则
     * 建议：按类型拆分解析服务（如用户解析、角色解析、资源解析分离）
     * </p>
     */
    public TypeResolutionServiceImpl(TypeDefinitionMapper typeDefinitionMapper,
                                     AbstractUserMapper abstractUserMapper,
                                     ResourceEntityMapper resourceEntityMapper,
                                     BizDomainMapper bizDomainMapper,
                                     AbstractRoleMapper abstractRoleMapper,
                                     OperationPermissionMapper operationPermissionMapper,
                                     RedisTemplate<String, Object> redisTemplate) {
        this.typeDefinitionMapper = typeDefinitionMapper;
        this.abstractUserMapper = abstractUserMapper;
        this.resourceEntityMapper = resourceEntityMapper;
        this.bizDomainMapper = bizDomainMapper;
        this.abstractRoleMapper = abstractRoleMapper;
        this.operationPermissionMapper = operationPermissionMapper;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 解析type_code到内部type_value
     * <p>
     * 使用Redis缓存，NULL值使用特殊标记防止缓存穿透
     * </p>
     *
     * @param tenantId 租户ID
     * @param typeKey  类型键
     * @param typeCode 类型编码
     * @return type_value整数值，未找到时返回null
     */
    @Override
    public Integer resolveTypeValue(Long tenantId, String typeKey, String typeCode) {
        String cacheKey = TYPE_VALUE_CACHE_KEY_PREFIX + tenantId + ":" + typeKey + ":" + typeCode;

        // 尝试从缓存获取
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            if (NULL_MARKER.equals(cached)) {
                return null;
            }
            if (cached instanceof Integer) {
                return (Integer) cached;
            }
        }

        // 缓存未命中，查询数据库
        TypeDefinition td = typeDefinitionMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(TypeDefinitionTableDef.TYPE_DEFINITION.TENANT_ID.eq(tenantId))
                .and(TypeDefinitionTableDef.TYPE_DEFINITION.TYPE_KEY.eq(typeKey))
                .and(TypeDefinitionTableDef.TYPE_DEFINITION.TYPE_CODE.eq(typeCode))
                .and(TypeDefinitionTableDef.TYPE_DEFINITION.DELETE_FLAG.eq(0))
        );

        Integer result = td != null ? td.getTypeValue() : null;

        // 缓存结果（NULL值使用NULL_MARKER标记，区别于缓存未命中）
        if (result != null) {
            redisTemplate.opsForValue().set(cacheKey, result, TYPE_CACHE_TTL_HOURS, TimeUnit.HOURS);
        } else {
            redisTemplate.opsForValue().set(cacheKey, NULL_MARKER, NULL_CACHE_TTL_SECONDS, TimeUnit.SECONDS);
        }

        return result;
    }

    /**
     * 批量解析type_codes到type_values
     *
     * @param tenantId 租户ID
     * @param typeKey  类型键
     * @param codes    类型编码集合
     * @return typeCode到typeValue的映射
     */
    @Override
    public Map<String, Integer> batchResolveTypeValues(Long tenantId, String typeKey, Set<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return Collections.emptyMap();
        }
        return typeDefinitionMapper.selectListByQuery(
            QueryWrapper.create()
                .where(TypeDefinitionTableDef.TYPE_DEFINITION.TENANT_ID.eq(tenantId))
                .and(TypeDefinitionTableDef.TYPE_DEFINITION.TYPE_KEY.eq(typeKey))
                .and(TypeDefinitionTableDef.TYPE_DEFINITION.TYPE_CODE.in(codes))
                .and(TypeDefinitionTableDef.TYPE_DEFINITION.DELETE_FLAG.eq(0))
        ).stream().collect(Collectors.toMap(
            TypeDefinition::getTypeCode,
            TypeDefinition::getTypeValue,
            (a, b) -> a
        ));
    }

    /**
     * 解析内部type_value回稳定的type_code
     *
     * @param tenantId  租户ID
     * @param typeKey   类型键
     * @param typeValue 类型值
     * @return 类型编码
     */
    @Override
    public String resolveTypeCode(Long tenantId, String typeKey, Integer typeValue) {
        if (typeValue == null) {
            return null;
        }

        String cacheKey = TYPE_CODE_CACHE_KEY_PREFIX + tenantId + ":" + typeKey + ":" + typeValue;

        // 尝试从缓存获取
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            if (NULL_MARKER.equals(cached)) {
                return null;
            }
            if (cached instanceof String) {
                return (String) cached;
            }
        }

        // 缓存未命中，查询数据库
        TypeDefinition td = typeDefinitionMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(TypeDefinitionTableDef.TYPE_DEFINITION.TENANT_ID.eq(tenantId))
                .and(TypeDefinitionTableDef.TYPE_DEFINITION.TYPE_KEY.eq(typeKey))
                .and(TypeDefinitionTableDef.TYPE_DEFINITION.TYPE_VALUE.eq(typeValue))
                .and(TypeDefinitionTableDef.TYPE_DEFINITION.DELETE_FLAG.eq(0))
        );

        String result = td != null ? td.getTypeCode() : null;

        // 缓存结果
        if (result != null) {
            redisTemplate.opsForValue().set(cacheKey, result, TYPE_CACHE_TTL_HOURS, TimeUnit.HOURS);
        } else {
            redisTemplate.opsForValue().set(cacheKey, NULL_MARKER, NULL_CACHE_TTL_SECONDS, TimeUnit.SECONDS);
        }

        return result;
    }

    /**
     * 批量解析type_values回type_codes
     *
     * @param tenantId 租户ID
     * @param typeKey  类型键
     * @param values   类型值集合
     * @return typeValue到typeCode的映射
     */
    @Override
    public Map<Integer, String> batchResolveTypeCodes(Long tenantId, String typeKey, Set<Integer> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyMap();
        }
        return typeDefinitionMapper.selectListByQuery(
            QueryWrapper.create()
                .where(TypeDefinitionTableDef.TYPE_DEFINITION.TENANT_ID.eq(tenantId))
                .and(TypeDefinitionTableDef.TYPE_DEFINITION.TYPE_KEY.eq(typeKey))
                .and(TypeDefinitionTableDef.TYPE_DEFINITION.TYPE_VALUE.in(values))
                .and(TypeDefinitionTableDef.TYPE_DEFINITION.DELETE_FLAG.eq(0))
        ).stream().collect(Collectors.toMap(
            TypeDefinition::getTypeValue,
            TypeDefinition::getTypeCode,
            (a, b) -> a
        ));
    }

    /**
     * 解析用户外部ID到内部用户ID
     *
     * @param tenantId          租户ID
     * @param subjectTypeCode   用户类型编码
     * @param subjectExternalId 用户外部ID
     * @return 用户内部ID
     */
    @Override
    public Long resolveUserId(Long tenantId, String subjectTypeCode, String subjectExternalId) {
        Integer userType = resolveTypeValue(tenantId, "user_type", subjectTypeCode);
        if (userType == null) return null;

        AbstractUser user = abstractUserMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(AbstractUserTableDef.ABSTRACT_USER.TENANT_ID.eq(tenantId))
                .and(AbstractUserTableDef.ABSTRACT_USER.USER_TYPE.eq(userType))
                .and(AbstractUserTableDef.ABSTRACT_USER.EXTERNAL_ID.eq(subjectExternalId))
                .and(AbstractUserTableDef.ABSTRACT_USER.DELETE_FLAG.eq(0))
        );
        return user != null ? user.getId() : null;
    }

    /**
     * 解析资源编码到内部资源ID
     *
     * @param tenantId         租户ID
     * @param resourceTypeCode 资源类型编码
     * @param resourceCode     资源编码
     * @param codeType         编码类型，null时默认"default"
     * @param domainCode       域编码，null表示全局
     * @return 资源内部ID
     */
    @Override
    public Long resolveResourceId(Long tenantId, String resourceTypeCode, String resourceCode,
                                  String codeType, String domainCode) {
        Integer resourceType = resolveTypeValue(tenantId, "resource_type", resourceTypeCode);
        if (resourceType == null) return null;

        String effectiveCodeType = (codeType != null && !codeType.isBlank()) ? codeType : PermConstants.CodeType.DEFAULT;

        QueryWrapper qw = QueryWrapper.create()
            .where(ResourceEntityTableDef.RESOURCE_ENTITY.TENANT_ID.eq(tenantId))
            .and(ResourceEntityTableDef.RESOURCE_ENTITY.RESOURCE_TYPE.eq(resourceType))
            .and(ResourceEntityTableDef.RESOURCE_ENTITY.CODE.eq(resourceCode))
            .and(ResourceEntityTableDef.RESOURCE_ENTITY.CODE_TYPE.eq(effectiveCodeType))
            .and(ResourceEntityTableDef.RESOURCE_ENTITY.DELETE_FLAG.eq(0));

        if (domainCode != null && !domainCode.isBlank()) {
            Long domainId = resolveDomainId(tenantId, domainCode);
            if (domainId == null) {
                return null;
            }
            qw.and(ResourceEntityTableDef.RESOURCE_ENTITY.BIZ_DOMAIN_ID.eq(domainId));
        }

        ResourceEntity resource = resourceEntityMapper.selectOneByQuery(qw);
        return resource != null ? resource.getId() : null;
    }

    /**
     * 解析操作编码到内部操作ID
     *
     * @param tenantId         租户ID
     * @param operationCode    操作编码
     * @param resourceTypeCode 资源类型编码，用于缩小范围
     * @return 操作内部ID
     */
    @Override
    public Long resolveOperationId(Long tenantId, String operationCode, String resourceTypeCode) {
        Integer resourceType = resolveTypeValue(tenantId, "resource_type", resourceTypeCode);

        QueryWrapper qw = QueryWrapper.create()
            .where(OperationPermissionTableDef.OPERATION_PERMISSION.TENANT_ID.eq(tenantId))
            .and(OperationPermissionTableDef.OPERATION_PERMISSION.CODE.eq(operationCode))
            .and(OperationPermissionTableDef.OPERATION_PERMISSION.DELETE_FLAG.eq(0));

        if (resourceType != null) {
            qw.and(OperationPermissionTableDef.OPERATION_PERMISSION.RESOURCE_TYPE.eq(resourceType));
        }

        OperationPermission op = operationPermissionMapper.selectOneByQuery(qw);
        return op != null ? op.getId() : null;
    }

    /**
     * 解析域编码到内部域ID
     *
     * @param tenantId   租户ID
     * @param domainCode 域编码
     * @return 域内部ID
     */
    @Override
    public Long resolveDomainId(Long tenantId, String domainCode) {
        if (domainCode == null || domainCode.isBlank()) return null;

        BizDomain domain = bizDomainMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(BizDomainTableDef.BIZ_DOMAIN.TENANT_ID.eq(tenantId))
                .and(BizDomainTableDef.BIZ_DOMAIN.CODE.eq(domainCode))
                .and(BizDomainTableDef.BIZ_DOMAIN.DELETE_FLAG.eq(0))
        );
        return domain != null ? domain.getId() : null;
    }

    /**
     * 解析角色外部ID到内部角色ID
     *
     * @param tenantId       租户ID
     * @param roleTypeCode   角色类型编码
     * @param roleExternalId 角色外部ID
     * @param domainCode     域编码，null表示全局
     * @return 角色内部ID
     */
    @Override
    public Long resolveRoleId(Long tenantId, String roleTypeCode, String roleExternalId, String domainCode) {
        Integer roleType = resolveTypeValue(tenantId, "role_type", roleTypeCode);
        if (roleType == null) return null;

        QueryWrapper qw = QueryWrapper.create()
            .where(AbstractRoleTableDef.ABSTRACT_ROLE.TENANT_ID.eq(tenantId))
            .and(AbstractRoleTableDef.ABSTRACT_ROLE.ROLE_TYPE.eq(roleType))
            .and(AbstractRoleTableDef.ABSTRACT_ROLE.EXTERNAL_ID.eq(roleExternalId))
            .and(AbstractRoleTableDef.ABSTRACT_ROLE.DELETE_FLAG.eq(0));

        if (domainCode != null && !domainCode.isBlank()) {
            Long domainId = resolveDomainId(tenantId, domainCode);
            if (domainId == null) {
                return null;
            }
            qw.and(AbstractRoleTableDef.ABSTRACT_ROLE.BIZ_DOMAIN_ID.eq(domainId));
        }

        AbstractRole role = abstractRoleMapper.selectOneByQuery(qw);
        return role != null ? role.getId() : null;
    }

    // ===== 批量解析实现 =====

    /**
     * 批量解析域编码到域ID
     *
     * @param tenantId    租户ID
     * @param domainCodes 域编码集合
     * @return domainCode到domainId的映射
     */
    @Override
    public Map<String, Long> batchResolveDomainIds(Long tenantId, Set<String> domainCodes) {
        if (domainCodes == null || domainCodes.isEmpty()) {
            return Collections.emptyMap();
        }
        // 过滤空值编码
        Set<String> validCodes = domainCodes.stream()
            .filter(code -> code != null && !code.isBlank())
            .collect(Collectors.toSet());
        if (validCodes.isEmpty()) {
            return Collections.emptyMap();
        }
        return bizDomainMapper.selectListByQuery(
            QueryWrapper.create()
                .where(BizDomainTableDef.BIZ_DOMAIN.TENANT_ID.eq(tenantId))
                .and(BizDomainTableDef.BIZ_DOMAIN.CODE.in(validCodes))
                .and(BizDomainTableDef.BIZ_DOMAIN.DELETE_FLAG.eq(0))
        ).stream().collect(Collectors.toMap(
            BizDomain::getCode,
            BizDomain::getId,
            (a, b) -> a
        ));
    }

    /**
     * 批量解析操作编码到操作ID
     *
     * @param tenantId         租户ID
     * @param resourceTypeCode 资源类型编码
     * @param operationCodes   操作编码集合
     * @return operationCode到operationId的映射
     */
    @Override
    public Map<String, Long> batchResolveOperationIds(Long tenantId, String resourceTypeCode, Set<String> operationCodes) {
        if (operationCodes == null || operationCodes.isEmpty()) {
            return Collections.emptyMap();
        }
        // 过滤空值编码
        Set<String> validCodes = operationCodes.stream()
            .filter(code -> code != null && !code.isBlank())
            .collect(Collectors.toSet());
        if (validCodes.isEmpty()) {
            return Collections.emptyMap();
        }
        Integer resourceType = resolveTypeValue(tenantId, "resource_type", resourceTypeCode);
        QueryWrapper qw = QueryWrapper.create()
            .where(OperationPermissionTableDef.OPERATION_PERMISSION.TENANT_ID.eq(tenantId))
            .and(OperationPermissionTableDef.OPERATION_PERMISSION.CODE.in(validCodes))
            .and(OperationPermissionTableDef.OPERATION_PERMISSION.DELETE_FLAG.eq(0));
        if (resourceType != null) {
            qw.and(OperationPermissionTableDef.OPERATION_PERMISSION.RESOURCE_TYPE.eq(resourceType));
        }
        return operationPermissionMapper.selectListByQuery(qw).stream().collect(Collectors.toMap(
            OperationPermission::getCode,
            OperationPermission::getId,
            (a, b) -> a
        ));
    }

    /**
     * 批量解析资源业务键到资源ID
     * <p>
     * 先批量解析类型和域，再按资源类型分组查询，避免N+1问题
     * </p>
     *
     * @param tenantId 租户ID
     * @param requests 资源解析请求列表
     * @return ResourceResolveKey到resourceId的映射
     */
    @Override
    public Map<ResourceResolveKey, Long> batchResolveResourceIds(Long tenantId, List<ResourceResolveRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return Collections.emptyMap();
        }
        // 批量解析所有唯一的资源类型编码
        Set<String> resourceTypeCodes = requests.stream()
            .map(ResourceResolveRequest::resourceTypeCode)
            .filter(code -> code != null && !code.isBlank())
            .collect(Collectors.toSet());
        Map<String, Integer> resourceTypeByCode = batchResolveTypeValues(tenantId, "resource_type", resourceTypeCodes);

        // 批量解析所有唯一的域编码
        Set<String> domainCodes = requests.stream()
            .map(ResourceResolveRequest::domainCode)
            .filter(code -> code != null && !code.isBlank())
            .collect(Collectors.toSet());
        Map<String, Long> domainIdByCode = batchResolveDomainIds(tenantId, domainCodes);

        Map<ResourceResolveKey, Long> result = new HashMap<>();

        // 按资源类型分组进行批量查询
        Map<Integer, List<ResourceResolveRequest>> byResourceType = requests.stream()
            .filter(r -> r.resourceTypeCode() != null && r.resourceCode() != null)
            .filter(r -> resourceTypeByCode.get(r.resourceTypeCode()) != null)
            .collect(Collectors.groupingBy(r -> resourceTypeByCode.get(r.resourceTypeCode())));

        for (Map.Entry<Integer, List<ResourceResolveRequest>> entry : byResourceType.entrySet()) {
            Integer resourceType = entry.getKey();
            List<ResourceResolveRequest> typeRequests = entry.getValue();

            // 收集该资源类型的所有编码
            Set<String> codes = typeRequests.stream()
                .map(ResourceResolveRequest::resourceCode)
                .filter(code -> code != null && !code.isBlank())
                .collect(Collectors.toSet());

            if (codes.isEmpty()) continue;

            // 查询该资源类型的所有匹配资源
            List<ResourceEntity> resources = resourceEntityMapper.selectListByQuery(
                QueryWrapper.create()
                    .where(ResourceEntityTableDef.RESOURCE_ENTITY.TENANT_ID.eq(tenantId))
                    .and(ResourceEntityTableDef.RESOURCE_ENTITY.RESOURCE_TYPE.eq(resourceType))
                    .and(ResourceEntityTableDef.RESOURCE_ENTITY.CODE.in(codes))
                    .and(ResourceEntityTableDef.RESOURCE_ENTITY.DELETE_FLAG.eq(0))
            );

            // 构建查找映射：code+codeType+domainId -> resource
            Map<String, ResourceEntity> resourceLookup = new HashMap<>();
            for (ResourceEntity res : resources) {
                String codeType = res.getCodeType() != null ? res.getCodeType() : PermConstants.CodeType.DEFAULT;
                String domainKey = res.getBizDomainId() != null ? String.valueOf(res.getBizDomainId()) : "";
                String lookupKey = res.getCode() + ":" + codeType + ":" + domainKey;
                resourceLookup.put(lookupKey, res);
            }

            // 匹配请求到资源
            for (ResourceResolveRequest req : typeRequests) {
                String codeType = req.codeType() != null && !req.codeType().isBlank() ? req.codeType() : PermConstants.CodeType.DEFAULT;
                Long domainId = req.domainCode() != null && !req.domainCode().isBlank()
                    ? domainIdByCode.get(req.domainCode()) : null;
                String domainKey = domainId != null ? String.valueOf(domainId) : "";
                String lookupKey = req.resourceCode() + ":" + codeType + ":" + domainKey;
                ResourceEntity res = resourceLookup.get(lookupKey);
                if (res != null) {
                    result.put(req.toKey(), res.getId());
                }
            }
        }

        return result;
    }

    /**
     * 批量解析用户外部ID到用户ID
     *
     * @param tenantId        租户ID
     * @param subjectTypeCode 用户类型编码
     * @param externalIds     外部ID集合
     * @return externalId到userId的映射
     */
    @Override
    public Map<String, Long> batchResolveUserIds(Long tenantId, String subjectTypeCode, Set<String> externalIds) {
        if (externalIds == null || externalIds.isEmpty() || subjectTypeCode == null || subjectTypeCode.isBlank()) {
            return Collections.emptyMap();
        }
        Integer userType = resolveTypeValue(tenantId, "user_type", subjectTypeCode);
        if (userType == null) {
            return Collections.emptyMap();
        }
        // 过滤空值外部ID
        Set<String> validIds = externalIds.stream()
            .filter(id -> id != null && !id.isBlank())
            .collect(Collectors.toSet());
        if (validIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return abstractUserMapper.selectListByQuery(
            QueryWrapper.create()
                .where(AbstractUserTableDef.ABSTRACT_USER.TENANT_ID.eq(tenantId))
                .and(AbstractUserTableDef.ABSTRACT_USER.USER_TYPE.eq(userType))
                .and(AbstractUserTableDef.ABSTRACT_USER.EXTERNAL_ID.in(validIds))
                .and(AbstractUserTableDef.ABSTRACT_USER.DELETE_FLAG.eq(0))
        ).stream().collect(Collectors.toMap(
            AbstractUser::getExternalId,
            AbstractUser::getId,
            (a, b) -> a
        ));
    }

    /**
     * 批量解析角色外部ID到角色ID
     *
     * @param tenantId       租户ID
     * @param roleTypeCode   角色类型编码
     * @param externalIds    外部ID集合
     * @param domainCode     域编码，null表示全局
     * @return externalId到roleId的映射
     */
    @Override
    public Map<String, Long> batchResolveRoleIds(Long tenantId, String roleTypeCode, Set<String> externalIds, String domainCode) {
        if (externalIds == null || externalIds.isEmpty() || roleTypeCode == null || roleTypeCode.isBlank()) {
            return Collections.emptyMap();
        }
        Integer roleType = resolveTypeValue(tenantId, "role_type", roleTypeCode);
        if (roleType == null) {
            return Collections.emptyMap();
        }
        // 过滤空值外部ID
        Set<String> validIds = externalIds.stream()
            .filter(id -> id != null && !id.isBlank())
            .collect(Collectors.toSet());
        if (validIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Long domainId = domainCode != null && !domainCode.isBlank()
            ? resolveDomainId(tenantId, domainCode) : null;

        QueryWrapper qw = QueryWrapper.create()
            .where(AbstractRoleTableDef.ABSTRACT_ROLE.TENANT_ID.eq(tenantId))
            .and(AbstractRoleTableDef.ABSTRACT_ROLE.ROLE_TYPE.eq(roleType))
            .and(AbstractRoleTableDef.ABSTRACT_ROLE.EXTERNAL_ID.in(validIds))
            .and(AbstractRoleTableDef.ABSTRACT_ROLE.DELETE_FLAG.eq(0));
        if (domainId != null) {
            qw.and(AbstractRoleTableDef.ABSTRACT_ROLE.BIZ_DOMAIN_ID.eq(domainId));
        } else {
            qw.and(AbstractRoleTableDef.ABSTRACT_ROLE.BIZ_DOMAIN_ID.isNull());
        }
        return abstractRoleMapper.selectListByQuery(qw).stream().collect(Collectors.toMap(
            AbstractRole::getExternalId,
            AbstractRole::getId,
            (a, b) -> a
        ));
    }

    /**
     * 检查类型定义是否为系统预设（不可删除）
     *
     * @param tenantId  租户ID
     * @param typeDefId 类型定义ID
     * @return true表示系统预设类型
     */
    @Override
    public boolean isSystemType(Long tenantId, Long typeDefId) {
        if (typeDefId == null) return false;
        TypeDefinition typeDef = typeDefinitionMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(TypeDefinitionTableDef.TYPE_DEFINITION.ID.eq(typeDefId))
                .and(TypeDefinitionTableDef.TYPE_DEFINITION.TENANT_ID.eq(tenantId))
                .and(TypeDefinitionTableDef.TYPE_DEFINITION.DELETE_FLAG.eq(0)));
        return typeDef != null && Boolean.TRUE.equals(typeDef.getIsSystem());
    }
}