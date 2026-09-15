package cn.ac.fage.accessmesh.access.type.service.domain.impl;

import cn.ac.fage.accessmesh.access.type.entity.OperationPermission;
import cn.ac.fage.accessmesh.access.type.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.access.type.service.domain.OperationPermissionDomainService;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 操作定义事实领域服务实现（Q-009 收敛产物，T-ACCESS-045）。
 * <p>
 * 硬契约见接口 javadoc：mapper-only 依赖、无缓存直读（全租户口径与新鲜读面均不接缓存）、
 * 不声明事务（REQUIRED 跟随调用方）。
 * </p>
 */
@Service
public class OperationPermissionDomainServiceImpl implements OperationPermissionDomainService {

    private final OperationPermissionMapper operationPermissionMapper;

    public OperationPermissionDomainServiceImpl(OperationPermissionMapper operationPermissionMapper) {
        this.operationPermissionMapper = operationPermissionMapper;
    }

    @Override
    public List<OperationPermission> selectAllOperationsByTenant(Long tenantId) {
        return operationPermissionMapper.selectByTenantAndResourceType(tenantId, null);
    }

    @Override
    public List<OperationPermission> selectByTenantAndResourceTypes(Long tenantId, Set<Integer> resourceTypes) {
        if (resourceTypes == null || resourceTypes.isEmpty()) {
            return Collections.emptyList();
        }
        return operationPermissionMapper.selectByTenantAndResourceTypes(tenantId, resourceTypes);
    }

    @Override
    public List<OperationPermission> selectByTenantResourceTypesAndOpCodes(Long tenantId,
                                                                           Set<Integer> resourceTypeValues,
                                                                           Set<String> operationCodes) {
        return operationPermissionMapper.selectByTenantResourceTypesAndOpCodes(
            tenantId, resourceTypeValues, operationCodes);
    }

    @Override
    public List<OperationPermission> selectValidByIds(Long tenantId, Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return operationPermissionMapper.selectValidByIds(tenantId, ids);
    }
}
