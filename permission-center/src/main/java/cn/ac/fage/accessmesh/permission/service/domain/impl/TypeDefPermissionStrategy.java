package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.permission.entity.TypeDefinition;
import cn.ac.fage.accessmesh.permission.mapper.TypeDefinitionMapper;
import cn.ac.fage.accessmesh.permission.service.domain.ResourcePermissionStrategy;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Component;

import static cn.ac.fage.accessmesh.permission.entity.table.TypeDefinitionTableDef.TYPE_DEFINITION;

/**
 * Strategy for TYPE_DEFINITION resource ID conversion.
 * Converts typeDefId to resource_entity.code via typeDefinition.typeCode lookup.
 *
 * <p>Note: Business rules (isSystem cannot be deleted) are handled in business layer,
 * NOT in this strategy. This strategy only handles ID conversion.
 */
@Component
public class TypeDefPermissionStrategy implements ResourcePermissionStrategy<Long> {

    private static final String TYPE_DEF_TYPE_CODE = "TYPE_DEFINITION";

    private final TypeDefinitionMapper typeDefinitionMapper;

    public TypeDefPermissionStrategy(TypeDefinitionMapper typeDefinitionMapper) {
        this.typeDefinitionMapper = typeDefinitionMapper;
    }

    @Override
    public String getResourceTypeCode() {
        return TYPE_DEF_TYPE_CODE;
    }

    @Override
    public String toResourceEntityCode(Long tenantId, Long typeDefId) {
        TypeDefinition typeDef = typeDefinitionMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(TYPE_DEFINITION.ID.eq(typeDefId))
                .and(TYPE_DEFINITION.TENANT_ID.eq(tenantId))
                .and(TYPE_DEFINITION.DELETE_FLAG.eq(0))
        );
        return typeDef != null ? typeDef.getTypeCode() : null;
    }

    /**
     * Check if a type definition is system-preset.
     * This is a business rule helper method, NOT used in permission validation.
     *
     * @param tenantId the tenant ID
     * @param typeDefId the type definition ID
     * @return true if isSystem=true, false otherwise
     */
    public boolean isSystemType(Long tenantId, Long typeDefId) {
        if (typeDefId == null) {
            return false;
        }
        TypeDefinition typeDef = typeDefinitionMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(TYPE_DEFINITION.ID.eq(typeDefId))
                .and(TYPE_DEFINITION.TENANT_ID.eq(tenantId))
                .and(TYPE_DEFINITION.DELETE_FLAG.eq(0))
        );
        return typeDef != null && Boolean.TRUE.equals(typeDef.getIsSystem());
    }
}