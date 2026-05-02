package cn.ac.fage.accessmesh.permission.service.domain.impl;

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
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.ac.fage.accessmesh.permission.entity.table.TypeDefinitionTableDef.TYPE_DEFINITION;
import static cn.ac.fage.accessmesh.permission.entity.table.AbstractUserTableDef.ABSTRACT_USER;
import static cn.ac.fage.accessmesh.permission.entity.table.ResourceEntityTableDef.RESOURCE_ENTITY;
import static cn.ac.fage.accessmesh.permission.entity.table.BizDomainTableDef.BIZ_DOMAIN;
import static cn.ac.fage.accessmesh.permission.entity.table.AbstractRoleTableDef.ABSTRACT_ROLE;
import static cn.ac.fage.accessmesh.permission.entity.table.OperationPermissionTableDef.OPERATION_PERMISSION;

/**
 * Resolves external stable business keys to internal database IDs.
 */
@Service
public class TypeResolutionServiceImpl implements TypeResolutionService {

    private final TypeDefinitionMapper typeDefinitionMapper;
    private final AbstractUserMapper abstractUserMapper;
    private final ResourceEntityMapper resourceEntityMapper;
    private final BizDomainMapper bizDomainMapper;
    private final AbstractRoleMapper abstractRoleMapper;
    private final OperationPermissionMapper operationPermissionMapper;

    public TypeResolutionServiceImpl(TypeDefinitionMapper typeDefinitionMapper,
                                     AbstractUserMapper abstractUserMapper,
                                     ResourceEntityMapper resourceEntityMapper,
                                     BizDomainMapper bizDomainMapper,
                                     AbstractRoleMapper abstractRoleMapper,
                                     OperationPermissionMapper operationPermissionMapper) {
        this.typeDefinitionMapper = typeDefinitionMapper;
        this.abstractUserMapper = abstractUserMapper;
        this.resourceEntityMapper = resourceEntityMapper;
        this.bizDomainMapper = bizDomainMapper;
        this.abstractRoleMapper = abstractRoleMapper;
        this.operationPermissionMapper = operationPermissionMapper;
    }

    @Override
    public Integer resolveTypeValue(Long tenantId, String typeKey, String typeCode) {
        TypeDefinition td = typeDefinitionMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(TYPE_DEFINITION.TENANT_ID.eq(tenantId))
                .and(TYPE_DEFINITION.TYPE_KEY.eq(typeKey))
                .and(TYPE_DEFINITION.TYPE_CODE.eq(typeCode))
                .and(TYPE_DEFINITION.DELETE_FLAG.eq(0))
        );
        return td != null ? td.getTypeValue() : null;
    }

    @Override
    public Map<String, Integer> batchResolveTypeValues(Long tenantId, String typeKey, Set<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return Collections.emptyMap();
        }
        return typeDefinitionMapper.selectListByQuery(
            QueryWrapper.create()
                .where(TYPE_DEFINITION.TENANT_ID.eq(tenantId))
                .and(TYPE_DEFINITION.TYPE_KEY.eq(typeKey))
                .and(TYPE_DEFINITION.TYPE_CODE.in(codes))
                .and(TYPE_DEFINITION.DELETE_FLAG.eq(0))
        ).stream().collect(Collectors.toMap(
            TypeDefinition::getTypeCode,
            TypeDefinition::getTypeValue,
            (a, b) -> a
        ));
    }

    @Override
    public String resolveTypeCode(Long tenantId, String typeKey, Integer typeValue) {
        if (typeValue == null) {
            return null;
        }
        TypeDefinition td = typeDefinitionMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(TYPE_DEFINITION.TENANT_ID.eq(tenantId))
                .and(TYPE_DEFINITION.TYPE_KEY.eq(typeKey))
                .and(TYPE_DEFINITION.TYPE_VALUE.eq(typeValue))
                .and(TYPE_DEFINITION.DELETE_FLAG.eq(0))
        );
        return td != null ? td.getTypeCode() : null;
    }

    @Override
    public Map<Integer, String> batchResolveTypeCodes(Long tenantId, String typeKey, Set<Integer> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyMap();
        }
        return typeDefinitionMapper.selectListByQuery(
            QueryWrapper.create()
                .where(TYPE_DEFINITION.TENANT_ID.eq(tenantId))
                .and(TYPE_DEFINITION.TYPE_KEY.eq(typeKey))
                .and(TYPE_DEFINITION.TYPE_VALUE.in(values))
                .and(TYPE_DEFINITION.DELETE_FLAG.eq(0))
        ).stream().collect(Collectors.toMap(
            TypeDefinition::getTypeValue,
            TypeDefinition::getTypeCode,
            (a, b) -> a
        ));
    }

    @Override
    public Long resolveUserId(Long tenantId, String subjectTypeCode, String subjectExternalId) {
        Integer userType = resolveTypeValue(tenantId, "user_type", subjectTypeCode);
        if (userType == null) return null;

        AbstractUser user = abstractUserMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(ABSTRACT_USER.TENANT_ID.eq(tenantId))
                .and(ABSTRACT_USER.USER_TYPE.eq(userType))
                .and(ABSTRACT_USER.EXTERNAL_ID.eq(subjectExternalId))
                .and(ABSTRACT_USER.DELETE_FLAG.eq(0))
        );
        return user != null ? user.getId() : null;
    }

    @Override
    public Long resolveResourceId(Long tenantId, String resourceTypeCode, String resourceCode,
                                  String codeType, String domainCode) {
        Integer resourceType = resolveTypeValue(tenantId, "resource_type", resourceTypeCode);
        if (resourceType == null) return null;

        String effectiveCodeType = (codeType != null && !codeType.isBlank()) ? codeType : "default";

        QueryWrapper qw = QueryWrapper.create()
            .where(RESOURCE_ENTITY.TENANT_ID.eq(tenantId))
            .and(RESOURCE_ENTITY.RESOURCE_TYPE.eq(resourceType))
            .and(RESOURCE_ENTITY.CODE.eq(resourceCode))
            .and(RESOURCE_ENTITY.CODE_TYPE.eq(effectiveCodeType))
            .and(RESOURCE_ENTITY.DELETE_FLAG.eq(0));

        if (domainCode != null && !domainCode.isBlank()) {
            Long domainId = resolveDomainId(tenantId, domainCode);
            if (domainId == null) {
                return null;
            }
            qw.and(RESOURCE_ENTITY.BIZ_DOMAIN_ID.eq(domainId));
        }

        ResourceEntity resource = resourceEntityMapper.selectOneByQuery(qw);
        return resource != null ? resource.getId() : null;
    }

    @Override
    public Long resolveOperationId(Long tenantId, String operationCode, String resourceTypeCode) {
        Integer resourceType = resolveTypeValue(tenantId, "resource_type", resourceTypeCode);

        QueryWrapper qw = QueryWrapper.create()
            .where(OPERATION_PERMISSION.TENANT_ID.eq(tenantId))
            .and(OPERATION_PERMISSION.CODE.eq(operationCode))
            .and(OPERATION_PERMISSION.DELETE_FLAG.eq(0));

        if (resourceType != null) {
            qw.and(OPERATION_PERMISSION.RESOURCE_TYPE.eq(resourceType));
        }

        OperationPermission op = operationPermissionMapper.selectOneByQuery(qw);
        return op != null ? op.getId() : null;
    }

    @Override
    public Long resolveDomainId(Long tenantId, String domainCode) {
        if (domainCode == null || domainCode.isBlank()) return null;

        BizDomain domain = bizDomainMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(BIZ_DOMAIN.TENANT_ID.eq(tenantId))
                .and(BIZ_DOMAIN.CODE.eq(domainCode))
                .and(BIZ_DOMAIN.DELETE_FLAG.eq(0))
        );
        return domain != null ? domain.getId() : null;
    }

    @Override
    public Long resolveRoleId(Long tenantId, String roleTypeCode, String roleExternalId, String domainCode) {
        Integer roleType = resolveTypeValue(tenantId, "role_type", roleTypeCode);
        if (roleType == null) return null;

        QueryWrapper qw = QueryWrapper.create()
            .where(ABSTRACT_ROLE.TENANT_ID.eq(tenantId))
            .and(ABSTRACT_ROLE.ROLE_TYPE.eq(roleType))
            .and(ABSTRACT_ROLE.EXTERNAL_ID.eq(roleExternalId))
            .and(ABSTRACT_ROLE.DELETE_FLAG.eq(0));

        if (domainCode != null && !domainCode.isBlank()) {
            Long domainId = resolveDomainId(tenantId, domainCode);
            if (domainId == null) {
                return null;
            }
            qw.and(ABSTRACT_ROLE.BIZ_DOMAIN_ID.eq(domainId));
        }

        AbstractRole role = abstractRoleMapper.selectOneByQuery(qw);
        return role != null ? role.getId() : null;
    }
}
