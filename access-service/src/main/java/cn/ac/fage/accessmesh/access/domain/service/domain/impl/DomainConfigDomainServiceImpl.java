package cn.ac.fage.accessmesh.access.domain.service.domain.impl;

import cn.ac.fage.accessmesh.access.domain.entity.DomainConfig;
import cn.ac.fage.accessmesh.access.domain.mapper.DomainConfigMapper;
import cn.ac.fage.accessmesh.access.domain.service.domain.DomainConfigDomainService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 业务域配置事实领域服务实现（Q-009 收敛产物，T-ACCESS-045）。
 * <p>
 * 硬契约见接口 javadoc：mapper-only 依赖、无缓存直读、不声明事务。
 * </p>
 */
@Service
public class DomainConfigDomainServiceImpl implements DomainConfigDomainService {

    private final DomainConfigMapper domainConfigMapper;

    public DomainConfigDomainServiceImpl(DomainConfigMapper domainConfigMapper) {
        this.domainConfigMapper = domainConfigMapper;
    }

    @Override
    public List<DomainConfig> selectByTenantId(Long tenantId) {
        return domainConfigMapper.selectByTenantId(tenantId);
    }
}
