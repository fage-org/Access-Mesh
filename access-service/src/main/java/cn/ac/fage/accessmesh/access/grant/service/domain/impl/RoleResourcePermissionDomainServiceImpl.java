package cn.ac.fage.accessmesh.access.grant.service.domain.impl;

import cn.ac.fage.accessmesh.access.grant.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.access.grant.service.domain.RoleResourcePermissionDomainService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 角色资源授权事实领域服务实现（Q-009 收敛产物，T-ACCESS-044）。
 * <p>
 * 硬契约见接口 javadoc：mapper-only 依赖、无缓存直读直写、不声明事务
 * （REQUIRED 跟随调用方——级联删除与内联回收全部运行在 AppService 声明的单事务内，
 * {@code selectReferencedConditionIds} 的同事务写后读语义依赖本类不加缓存与独立事务）。
 * </p>
 */
@Service
public class RoleResourcePermissionDomainServiceImpl implements RoleResourcePermissionDomainService {

    private final RoleResourcePermissionMapper roleResourcePermissionMapper;

    public RoleResourcePermissionDomainServiceImpl(RoleResourcePermissionMapper roleResourcePermissionMapper) {
        this.roleResourcePermissionMapper = roleResourcePermissionMapper;
    }

    @Override
    public List<Long> selectValidPermIdsByResourceIds(Long tenantId, List<Long> resourceIds) {
        if (resourceIds == null || resourceIds.isEmpty()) {
            return Collections.emptyList();
        }
        return roleResourcePermissionMapper.selectValidPermIdsByResourceIds(tenantId, resourceIds);
    }

    @Override
    public Set<Long> selectRoleIdsByResourceIds(Long tenantId, List<Long> resourceIds) {
        if (resourceIds == null || resourceIds.isEmpty()) {
            return Collections.emptySet();
        }
        return roleResourcePermissionMapper.selectRoleIdsByResourceIds(tenantId, resourceIds);
    }

    @Override
    public List<Long> selectValidPermIdsByResourceTypes(Long tenantId, Set<Integer> resourceTypes) {
        if (resourceTypes == null || resourceTypes.isEmpty()) {
            return Collections.emptyList();
        }
        return roleResourcePermissionMapper.selectValidPermIdsByResourceTypes(tenantId, resourceTypes);
    }

    @Override
    public Set<Long> selectRoleIdsByResourceTypes(Long tenantId, Set<Integer> resourceTypes) {
        if (resourceTypes == null || resourceTypes.isEmpty()) {
            return Collections.emptySet();
        }
        return roleResourcePermissionMapper.selectRoleIdsByResourceTypes(tenantId, resourceTypes);
    }

    @Override
    public Set<Long> selectConditionIdsByPermIds(Long tenantId, List<Long> permissionIds) {
        if (permissionIds == null || permissionIds.isEmpty()) {
            return Collections.emptySet();
        }
        return roleResourcePermissionMapper.selectConditionIdsByPermIds(tenantId, permissionIds);
    }

    @Override
    public Set<Long> selectReferencedOperationBits(Long tenantId, Integer resourceType, Set<Long> operationBits) {
        if (operationBits == null || operationBits.isEmpty()) {
            return Collections.emptySet();
        }
        return roleResourcePermissionMapper.selectReferencedOperationBits(tenantId, resourceType, operationBits);
    }

    @Override
    public Set<Long> selectReferencedConditionIds(Long tenantId, Set<Long> conditionIds) {
        if (conditionIds == null || conditionIds.isEmpty()) {
            return Collections.emptySet();
        }
        return roleResourcePermissionMapper.selectReferencedConditionIds(tenantId, conditionIds);
    }

    @Override
    public Set<String> selectServiceCodesByConditionIds(Long tenantId, Set<Long> conditionIds) {
        if (conditionIds == null || conditionIds.isEmpty()) {
            return Collections.emptySet();
        }
        return roleResourcePermissionMapper.selectServiceCodesByConditionIds(tenantId, conditionIds);
    }

    @Override
    public int softDeleteBatch(Long tenantId, List<Long> ids, LocalDateTime deletedAt) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        return roleResourcePermissionMapper.softDeleteBatch(tenantId, ids, deletedAt);
    }
}
