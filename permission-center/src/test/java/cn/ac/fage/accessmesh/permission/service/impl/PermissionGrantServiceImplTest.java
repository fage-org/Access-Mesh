package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.permission.dto.req.RolePermissionAddChildReq;
import cn.ac.fage.accessmesh.permission.entity.DomainConfig;
import cn.ac.fage.accessmesh.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.permission.entity.RoleResourcePermission;
import cn.ac.fage.accessmesh.permission.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.permission.mapper.DomainConfigMapper;
import cn.ac.fage.accessmesh.permission.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.permission.mapper.PermissionConditionMapper;
import cn.ac.fage.accessmesh.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.permission.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.permission.service.domain.OperationLogDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.OperationPermissionDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionChangeDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionVersionDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.ResourceDependencyDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.RolePermissionDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.permission.service.domain.UserRoleDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.AbstractRoleDomainService;
import cn.ac.fage.accessmesh.permission.service.AuthorizationService;
import cn.ac.fage.accessmesh.permission.service.domain.impl.PermQueryEngine;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 权限授予服务测试类
 * <p>
 * 测试PermissionGrantServiceImpl的各项功能：
 * - 子权限添加时的编码精确匹配拒绝逻辑
 * - scopeAll为false时的资源编码必填校验逻辑
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class PermissionGrantServiceImplTest {

    /** 抽象角色Mapper Mock */
    @Mock private AbstractRoleMapper abstractRoleMapper;
    /** 资源实体Mapper Mock */
    @Mock private ResourceEntityMapper resourceEntityMapper;
    /** 操作权限Mapper Mock */
    @Mock private OperationPermissionMapper operationPermissionMapper;
    /** 域配置Mapper Mock */
    @Mock private DomainConfigMapper domainConfigMapper;
    /** 权限条件Mapper Mock */
    @Mock private PermissionConditionMapper permissionConditionMapper;
    /** 角色资源权限Mapper Mock */
    @Mock private RoleResourcePermissionMapper rolePermMapper;
    /** 角色权限领域服务Mock */
    @Mock private RolePermissionDomainService rolePermissionDomainService;
    /** 权限版本领域服务Mock */
    @Mock private PermissionVersionDomainService permissionVersionDomainService;
    /** 权限变更领域服务Mock */
    @Mock private PermissionChangeDomainService permissionChangeDomainService;
    /** 操作日志领域服务Mock */
    @Mock private OperationLogDomainService operationLogDomainService;
    /** 用户角色领域服务Mock */
    @Mock private UserRoleDomainService userRoleDomainService;
    /** 资源依赖领域服务Mock */
    @Mock private ResourceDependencyDomainService resourceDependencyDomainService;
    /** 类型解析服务Mock */
    @Mock private TypeResolutionService typeResolutionService;
    /** 授权服务Mock */
    @Mock private AuthorizationService authorizationService;
    /** 操作权限领域服务Mock */
    @Mock private OperationPermissionDomainService operationPermissionDomainService;
    /** 抽象角色领域服务Mock */
    @Mock private AbstractRoleDomainService abstractRoleDomainService;
    /** 权限查询引擎Mock */
    @Mock private PermQueryEngine engine;

    /** 待测试的权限授予服务实例 */
    private PermissionGrantServiceImpl service;

    /**
     * 测试前置初始化
     * <p>
     * 在每个测试方法执行前初始化PermissionGrantServiceImpl实例，
     * 注入所有Mock依赖对象。
     * </p>
     */
    @BeforeEach
    void setUp() {
        service = new PermissionGrantServiceImpl(
            abstractRoleMapper, resourceEntityMapper, operationPermissionMapper, domainConfigMapper, permissionConditionMapper,
            rolePermMapper, rolePermissionDomainService, permissionVersionDomainService, permissionChangeDomainService,
            operationLogDomainService, userRoleDomainService, resourceDependencyDomainService, typeResolutionService,
            authorizationService, operationPermissionDomainService, abstractRoleDomainService, engine
        );
    }

    /**
     * 测试子权限通过精确编码匹配被拒绝的逻辑
     * <p>
     * 当父权限的业务域配置中已存在与请求子权限编码精确匹配的资源类型时，
     * addChildren应抛出BizException异常，拒绝添加子权限。
     * </p>
     */
    @Test
    @Disabled("Requires OperatorContext mock setup")
    void shouldRejectSubPermByExactCodeMatch() {
        RoleResourcePermission parent = new RoleResourcePermission();
        parent.setId(10L);
        parent.setTenantId(1L);
        parent.setDeleteFlag(0L);
        parent.setAbstractRoleId(20L);
        parent.setResourceEntityId(100L);
        when(rolePermMapper.selectOneById(10L)).thenReturn(parent);

        ResourceEntity parentRes = new ResourceEntity();
        parentRes.setId(100L);
        parentRes.setBizDomainId(99L);
        when(resourceEntityMapper.selectOneById(100L)).thenReturn(parentRes);

        DomainConfig config = new DomainConfig();
        config.setExtra("USER,DEPT");
        when(domainConfigMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(config);
        when(typeResolutionService.resolveTypeValue(1L, "resource_type", "SER")).thenReturn(3);

        RolePermissionAddChildReq req = new RolePermissionAddChildReq(
            10L, List.of(new RolePermissionAddChildReq.ChildItem("SER", "x", "default", "VIEW", false, null, null))
        );

        assertThrows(BizException.class, () -> service.addChildren(1L, req));
    }

    /**
     * 测试scopeAll为false时资源编码必填的校验逻辑
     * <p>
     * 当添加子权限请求中scopeAll设置为false且resourceCode为null时，
     * addChildren应抛出BizException异常，
     * 要求必须提供资源编码以确定权限范围。
     * </p>
     */
    @Test
    @Disabled("Requires OperatorContext mock setup")
    void shouldRequireResourceCodeWhenScopeAllFalse() {
        RoleResourcePermission parent = new RoleResourcePermission();
        parent.setId(10L);
        parent.setTenantId(1L);
        parent.setDeleteFlag(0L);
        parent.setAbstractRoleId(20L);
        when(rolePermMapper.selectOneById(10L)).thenReturn(parent);
        when(typeResolutionService.resolveTypeValue(1L, "resource_type", "DATA")).thenReturn(4);
        when(typeResolutionService.resolveOperationId(1L, "DATA_READ", "DATA")).thenReturn(11L);

        RolePermissionAddChildReq req = new RolePermissionAddChildReq(
            10L, List.of(new RolePermissionAddChildReq.ChildItem("DATA", null, "default", "DATA_READ", false, null, null))
        );

        assertThrows(BizException.class, () -> service.addChildren(1L, req));
    }
}