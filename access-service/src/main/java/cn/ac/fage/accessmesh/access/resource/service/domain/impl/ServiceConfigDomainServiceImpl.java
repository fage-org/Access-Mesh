package cn.ac.fage.accessmesh.access.resource.service.domain.impl;

import cn.ac.fage.accessmesh.access.resource.entity.ServiceConfig;
import cn.ac.fage.accessmesh.access.resource.mapper.ServiceConfigMapper;
import cn.ac.fage.accessmesh.access.resource.service.domain.ServiceConfigDomainService;
import org.springframework.stereotype.Service;

/**
 * 服务配置事实领域服务实现（Q-009 收敛产物，T-ACCESS-045）。
 * <p>
 * 硬契约见接口 javadoc：mapper-only 依赖、无缓存直读、不声明事务。
 * </p>
 */
@Service
public class ServiceConfigDomainServiceImpl implements ServiceConfigDomainService {

    private final ServiceConfigMapper serviceConfigMapper;

    public ServiceConfigDomainServiceImpl(ServiceConfigMapper serviceConfigMapper) {
        this.serviceConfigMapper = serviceConfigMapper;
    }

    @Override
    public ServiceConfig selectByTenantAndServiceCode(Long tenantId, String serviceCode) {
        return serviceConfigMapper.selectByTenantAndServiceCode(tenantId, serviceCode);
    }
}
