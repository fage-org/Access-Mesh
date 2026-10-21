package cn.ac.fage.accessmesh.access.type.service.domain.impl;

import cn.ac.fage.accessmesh.access.type.entity.TypeDefinition;
import cn.ac.fage.accessmesh.access.type.mapper.TypeDefinitionMapper;
import cn.ac.fage.accessmesh.access.type.service.domain.TypeDefinitionDomainService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * 类型定义事实领域服务实现（Q-009 收敛产物，T-ACCESS-045）。
 * <p>
 * 硬契约见接口 javadoc：mapper-only 依赖、无缓存直读、不声明事务。
 * </p>
 */
@Service
public class TypeDefinitionDomainServiceImpl implements TypeDefinitionDomainService {

    private final TypeDefinitionMapper typeDefinitionMapper;

    public TypeDefinitionDomainServiceImpl(TypeDefinitionMapper typeDefinitionMapper) {
        this.typeDefinitionMapper = typeDefinitionMapper;
    }

    @Override
    public List<TypeDefinition> selectResourceTypesByValues(Long tenantId, Set<Integer> values) {
        return values.isEmpty() ? List.of() : typeDefinitionMapper.selectByTypeKeyAndValues(tenantId, "resource_type", values);
    }

    @Override
    public List<TypeDefinition> selectValidByIds(Long tenantId, Set<Long> ids) {
        return ids.isEmpty() ? List.of() : typeDefinitionMapper.selectValidByIds(tenantId, ids);
    }

    @Override
    public List<TypeDefinition> selectValidByTenant(Long tenantId) {
        return typeDefinitionMapper.selectValidByTenant(tenantId);
    }

    @Override
    public List<TypeDefinition> selectByTenantAndTypeKey(Long tenantId, String typeKey) {
        return typeDefinitionMapper.selectByTenantAndTypeKey(tenantId, typeKey);
    }
}
