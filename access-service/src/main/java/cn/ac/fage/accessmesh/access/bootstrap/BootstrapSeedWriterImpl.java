package cn.ac.fage.accessmesh.access.bootstrap;

import cn.ac.fage.accessmesh.access.sync.guard.LocalProjectionOwner;
import cn.ac.fage.accessmesh.access.projection.PermConstants;
import cn.ac.fage.accessmesh.access.role.entity.AbstractRole;
import cn.ac.fage.accessmesh.access.type.entity.OperationPermission;
import cn.ac.fage.accessmesh.access.resource.entity.ResourceApiMapping;
import cn.ac.fage.accessmesh.access.resource.entity.ResourceEntity;
import cn.ac.fage.accessmesh.access.grant.entity.RoleResourcePermission;
import cn.ac.fage.accessmesh.access.role.entity.UserRole;
import cn.ac.fage.accessmesh.access.role.entity.table.AbstractRoleTableDef;
import cn.ac.fage.accessmesh.access.type.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.role.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.access.type.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.access.resource.mapper.ResourceApiMappingMapper;
import cn.ac.fage.accessmesh.access.resource.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.access.grant.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.access.role.mapper.UserRoleMapper;
import cn.ac.fage.accessmesh.access.platform.entity.SysJob;
import cn.ac.fage.accessmesh.access.platform.mapper.SysJobMapper;
import cn.ac.fage.accessmesh.access.bootstrap.BootstrapSeedWriter;
import cn.ac.fage.accessmesh.access.grant.service.domain.PermissionGrantDomainService;
import cn.ac.fage.accessmesh.access.grant.service.domain.PermissionGrantPlanDomainService;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * {@link BootstrapSeedWriter} 实现（包内可见——bootstrap 专用，仅经接口被
 * access.bootstrap initializer 注入，禁止业务调用方直接引用实现类）。
 * <p>
 * 写入语义对齐现有链路：资源/映射与 RESOURCE_SYNC 链路同款唯一键定位语义但走 MANUAL
 * 维护来源（bootstrap 是内部种子而非服务同步）；绑定与管理链路 assignRole 直插形态一致；
 * 授权复用 {@link PermissionGrantPlanDomainService#apply} 的 MANUAL 落库管线（单操作位
 * CHECK、子权限级联等不变量全部生效；注意 apply 不经 prevalidate，20042 条件启用校验
 * 不在本路径——固定图从不携带条件授权，无适用面）。
 * </p>
 */
@Component
class BootstrapSeedWriterImpl implements BootstrapSeedWriter {

    private final AbstractRoleMapper abstractRoleMapper;
    private final ResourceEntityMapper resourceEntityMapper;
    private final ResourceApiMappingMapper resourceApiMappingMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleResourcePermissionMapper roleResourcePermissionMapper;
    private final OperationPermissionMapper operationPermissionMapper;
    private final PermissionGrantDomainService permissionGrantDomainService;
    private final PermissionGrantPlanDomainService permissionGrantPlanDomainService;
    private final SysJobMapper sysJobMapper;

    BootstrapSeedWriterImpl(AbstractRoleMapper abstractRoleMapper,
                            ResourceEntityMapper resourceEntityMapper,
                            ResourceApiMappingMapper resourceApiMappingMapper,
                            UserRoleMapper userRoleMapper,
                            RoleResourcePermissionMapper rolePermissionMapper,
                            OperationPermissionMapper operationPermissionMapper,
                            PermissionGrantDomainService permissionGrantDomainService,
                            PermissionGrantPlanDomainService permissionGrantPlanDomainService,
                            SysJobMapper sysJobMapper) {
        this.abstractRoleMapper = abstractRoleMapper;
        this.resourceEntityMapper = resourceEntityMapper;
        this.resourceApiMappingMapper = resourceApiMappingMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleResourcePermissionMapper = rolePermissionMapper;
        this.operationPermissionMapper = operationPermissionMapper;
        this.permissionGrantDomainService = permissionGrantDomainService;
        this.permissionGrantPlanDomainService = permissionGrantPlanDomainService;
        this.sysJobMapper = sysJobMapper;
    }

    @Override
    public AbstractRole findRoleByExternalId(Long tenantId, String externalId) {
        QueryWrapper qw = QueryWrapper.create()
            .where(AbstractRoleTableDef.ABSTRACT_ROLE.TENANT_ID.eq(tenantId))
            .and(AbstractRoleTableDef.ABSTRACT_ROLE.EXTERNAL_ID.eq(externalId))
            .and(AbstractRoleTableDef.ABSTRACT_ROLE.DELETE_FLAG.eq(0L));
        return abstractRoleMapper.selectOneByQuery(qw);
    }

    @Override
    public List<ResourceEntity> findResources(Long tenantId, Integer resourceTypeValue, Set<String> codes) {
        if (codes.isEmpty()) {
            return List.of();
        }
        return resourceEntityMapper.selectByTypeAndCodesAndCodeTypes(
            tenantId, resourceTypeValue, codes, Set.of(PermConstants.CodeType.DEFAULT));
    }

    @Override
    public List<ResourceApiMapping> findMappings(Long tenantId, Set<Long> resourceEntityIds) {
        if (resourceEntityIds.isEmpty()) {
            return List.of();
        }
        return resourceApiMappingMapper.selectByResourceEntityIds(tenantId, resourceEntityIds);
    }

    @Override
    public List<RoleResourcePermission> findValidGrants(Long tenantId, Long roleId) {
        return roleResourcePermissionMapper.selectValidByRoleIds(tenantId, Set.of(roleId));
    }

    @Override
    public List<RoleResourcePermission> findSoftDeletedGrants(Long tenantId, Long roleId) {
        return roleResourcePermissionMapper.selectSoftDeletedByRoleIds(tenantId, Set.of(roleId));
    }

    @Override
    public List<UserRole> findValidBindings(Long tenantId, Long subjectId, Long roleId) {
        return userRoleMapper.selectValidByUserIdsAndTargetIds(
            tenantId, Set.of(subjectId), Set.of(roleId), ResourceTypeCode.ROLE);
    }

    @Override
    public List<OperationPermission> findOperations(Long tenantId, Set<Integer> resourceTypeValues,
                                                    Set<String> operationCodes) {
        return operationPermissionMapper.selectByTenantResourceTypesAndOpCodes(
            tenantId, resourceTypeValues, operationCodes);
    }

    @Override
    public Long insertResource(Long tenantId, Integer resourceTypeValue, String code, String name) {
        LocalDateTime now = LocalDateTime.now();
        ResourceEntity resource = new ResourceEntity();
        resource.setTenantId(tenantId);
        resource.setResourceType(resourceTypeValue);
        resource.setCode(code);
        resource.setCodeType(PermConstants.CodeType.DEFAULT);
        resource.setName(name);
        resource.setStatus(1);
        resource.setOwnerServiceCode(LocalProjectionOwner.SERVICE_CODE);
        resource.setMaintainSource(PermConstants.MaintainSource.MANUAL);
        resource.setCreatedAt(now);
        resource.setUpdatedAt(now);
        resource.setDeleteFlag(0L);
        resourceEntityMapper.insert(resource);
        return resource.getId();
    }

    @Override
    public void insertApiMapping(Long tenantId, Long resourceEntityId, String httpMethod, String pathPattern) {
        LocalDateTime now = LocalDateTime.now();
        ResourceApiMapping mapping = new ResourceApiMapping();
        mapping.setTenantId(tenantId);
        mapping.setResourceEntityId(resourceEntityId);
        mapping.setServiceCode(LocalProjectionOwner.SERVICE_CODE);
        mapping.setHttpMethod(httpMethod.toUpperCase());
        mapping.setPathPattern(pathPattern);
        mapping.setMatchOrder(0);
        mapping.setEnabled(true);
        mapping.setCreatedAt(now);
        mapping.setUpdatedAt(now);
        mapping.setDeleteFlag(0L);
        resourceApiMappingMapper.insert(mapping);
    }

    @Override
    public void insertRoleBinding(Long tenantId, Long subjectId, Long roleId) {
        LocalDateTime now = LocalDateTime.now();
        UserRole binding = new UserRole();
        binding.setTenantId(tenantId);
        binding.setAbstractUserId(subjectId);
        binding.setTargetType(ResourceTypeCode.ROLE);
        binding.setTargetId(roleId);
        binding.setCreatedAt(now);
        binding.setUpdatedAt(now);
        binding.setDeleteFlag(0L);
        userRoleMapper.insert(binding);
    }

    @Override
    public void insertGrants(Long tenantId, Long roleId, List<RoleResourcePermission> grants) {
        if (grants.isEmpty()) {
            return;
        }
        // T-PERM-062：种子直写通道下沉至 PermissionGrantPlanDomainService.seedGrants
        // （bootstrap 固定图与类型授权根共用；保留两条领域校验 + apply 落库管线，幂等
        // insert-if-absent），本组件不再各自持有一份行构造校验逻辑
        permissionGrantPlanDomainService.seedGrants(tenantId, roleId, grants);
    }

    /**
     * 并发窗口注记（2026-09-21 用户定案：接受现状）：多副本同时冷启动可双插同 invokeTarget
     * 任务行（check-then-insert 无 DB 唯一索引兜底，其余 bootstrap 种子均有 uk）；后果=管理员
     * 启用后对账多跑几次，只读无正确性危害——不为该极窄窗口动权威 DDL/迁移面。
     */
    @Override
    public boolean insertJobIfAbsent(Long tenantId, String jobName, String invokeTarget, String cronExpression) {
        if (sysJobMapper.selectValidByInvokeTarget(tenantId, invokeTarget) != null) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        SysJob job = new SysJob();
        job.setTenantId(tenantId);
        job.setJobName(jobName);
        job.setJobGroup("DEFAULT");
        job.setInvokeTarget(invokeTarget);
        job.setCronExpression(cronExpression);
        job.setMisfirePolicy(1);
        // 用户定案（2026-09-21）：种子默认停用——对账只发现异常且正常链路同事务保证一致，
        // 默认零后台负载；管理员经任务管理页手动触发或启用周期巡检
        job.setStatus(0);
        job.setRemark("bootstrap 系统任务种子（T-PERM-073）；默认停用，按需启用");
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        job.setDeleteFlag(0L);
        sysJobMapper.insert(job);
        return true;
    }
}
