package cn.ac.fage.accessmesh.access.resource.service.domain.impl;

import cn.ac.fage.accessmesh.access.resource.entity.ResourceApiMapping;
import cn.ac.fage.accessmesh.access.resource.mapper.ResourceApiMappingMapper;
import cn.ac.fage.accessmesh.access.resource.service.domain.ResourceApiMappingDomainService;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 资源 API 映射事实领域服务实现（Q-009 收敛产物，T-ACCESS-045）。
 * <p>
 * 硬契约见接口 javadoc：mapper-only 依赖、无缓存直读、不声明事务。
 * </p>
 */
@Service
public class ResourceApiMappingDomainServiceImpl implements ResourceApiMappingDomainService {

    private final ResourceApiMappingMapper resourceApiMappingMapper;

    public ResourceApiMappingDomainServiceImpl(ResourceApiMappingMapper resourceApiMappingMapper) {
        this.resourceApiMappingMapper = resourceApiMappingMapper;
    }

    @Override
    public List<ResourceApiMapping> selectByResourceEntityIds(Long tenantId, Set<Long> resourceEntityIds) {
        if (resourceEntityIds == null || resourceEntityIds.isEmpty()) {
            return Collections.emptyList();
        }
        return resourceApiMappingMapper.selectByResourceEntityIds(tenantId, resourceEntityIds);
    }
}
