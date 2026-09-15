package cn.ac.fage.accessmesh.access.grant.service.domain.impl;

import cn.ac.fage.accessmesh.access.grant.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.access.grant.service.domain.RoleResourcePermissionDomainService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
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
        return roleResourcePermissionMapper.selectValidPermIdsByResourceIds(tenantId, resourceIds);
    }

    @Override
    public Set<Long> selectRoleIdsByResourceIds(Long tenantId, List<Long> resourceIds) {
        return roleResourcePermissionMapper.selectRoleIdsByResourceIds(tenantId, resourceIds);
    }

    @Override
    public List<Long> selectValidPermIdsByResourceTypes(Long tenantId, Set<Integer> resourceTypes) {
        return roleResourcePermissionMapper.selectValidPermIdsByResourceTypes(tenantId, resourceTypes);
    }

    @Override
    public Set<Long> selectRoleIdsByResourceTypes(Long tenantId, Set<Integer> resourceTypes) {
        return roleResourcePermissionMapper.selectRoleIdsByResourceTypes(tenantId, resourceTypes);
    }

    @Override
    public Set<Long> selectConditionIdsByPermIds(Long tenantId, List<Long> permissionIds) {
        return roleResourcePermissionMapper.selectConditionIdsByPermIds(tenantId, permissionIds);
    }

    @Override
    public Set<Long> selectReferencedConditionIds(Long tenantId, Set<Long> conditionIds) {
        return roleResourcePermissionMapper.selectReferencedConditionIds(tenantId, conditionIds);
    }

    @Override
    public Set<String> selectServiceCodesByConditionIds(Long tenantId, Set<Long> conditionIds) {
        return roleResourcePermissionMapper.selectServiceCodesByConditionIds(tenantId, conditionIds);
    }

    @Override
    public int softDeleteBatch(Long tenantId, List<Long> ids, LocalDateTime deletedAt) {
        return roleResourcePermissionMapper.softDeleteBatch(tenantId, ids, deletedAt);
    }
}
