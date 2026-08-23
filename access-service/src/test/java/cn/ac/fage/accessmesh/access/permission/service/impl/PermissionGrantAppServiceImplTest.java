package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.access.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.access.permission.dto.req.RolePermissionAddChildReq;
import cn.ac.fage.accessmesh.access.permission.entity.DomainConfig;
import cn.ac.fage.accessmesh.access.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.access.permission.entity.RoleResourcePermission;
import cn.ac.fage.accessmesh.access.permission.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.DomainConfigMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.PermissionConditionMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.access.permission.service.domain.AuditDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.PermissionGrantDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.PermissionGrantPlanDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.SubjectDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.access.permission.service.domain.DomainClassifyService;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.access.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.permission.util.OperatorContext;
import cn.ac.fage.accessmesh.perm.common.enums.ScopeMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 权限授予服务测试类
 * <p>
 * 测试PermissionGrantAppServiceImpl的各项功能：
 * - 子权限添加时的编码精确匹配拒绝逻辑
 * - scopeAll为false时的资源编码必填校验逻辑
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class PermissionGrantAppServiceImplTest {

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
    /** 权限授予领域服务Mock */
    @Mock private PermissionGrantDomainService permissionGrantDomainService;
    @Mock private PermissionGrantPlanDomainService permissionGrantPlanDomainService;
    /** 审计领域服务Mock */
    @Mock private AuditDomainService auditDomainService;
    /** 主体领域服务Mock */
    @Mock private SubjectDomainService subjectDomainService;
    /** 类型解析服务Mock */
    @Mock private TypeResolutionService typeResolutionService;
    /** 域分类领域服务Mock */
    @Mock private DomainClassifyService domainClassifyService;
    /** 权限查询引擎Mock */
    @Mock private PermQueryEngine engine;

    /** 待测试的权限授予服务实例 */
    private PermissionGrantAppServiceImpl service;

    /**
     * 测试前置初始化
     */
    @BeforeEach
    void setUp() {
        service = new PermissionGrantAppServiceImpl(
            abstractRoleMapper, resourceEntityMapper, operationPermissionMapper, domainConfigMapper, permissionConditionMapper,
            rolePermMapper, permissionGrantDomainService, permissionGrantPlanDomainService,
            auditDomainService, subjectDomainService, typeResolutionService,
            domainClassifyService, engine
        );
    }

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
        parentRes.setResourceType(3);
        when(resourceEntityMapper.selectOneById(100L)).thenReturn(parentRes);

        when(typeResolutionService.resolveTypeCode(1L, "resource_type", 3)).thenReturn("SER");
        when(domainClassifyService.findDomainIdByTypeCode(1L, "SER")).thenReturn(99L);

        DomainConfig config = new DomainConfig();
        config.setExtra("USER,DEPT");
        when(domainConfigMapper.selectValidByTypeString(any(), any(), anyString())).thenReturn(config);
        when(typeResolutionService.resolveTypeValue(1L, "resource_type", "SER")).thenReturn(3);

        RolePermissionAddChildReq req = new RolePermissionAddChildReq(
            10L, List.of(new RolePermissionAddChildReq.ChildItem("SER", "x", "default", "VIEW", ScopeMode.INSTANCE, null, null))
        );

        assertThrows(BizException.class, () -> service.addChildren(1L, req));
    }

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
            10L, List.of(new RolePermissionAddChildReq.ChildItem("DATA", null, "default", "DATA_READ", ScopeMode.INSTANCE, null, null))
        );

        assertThrows(BizException.class, () -> service.addChildren(1L, req));
    }

    @Test
    void shouldRejectChildWhenOperatorCannotDelegate() {
        RoleResourcePermission parent = new RoleResourcePermission();
        parent.setId(10L);
        parent.setTenantId(1L);
        parent.setAbstractRoleId(20L);
        when(rolePermMapper.selectValidById(1L, null, 10L)).thenReturn(parent);
        when(engine.hasPermissionByCode(
            1L, 10L, ResourceTypeCode.ROLE, "20", OperationCodeConstants.MANAGE)).thenReturn(true);

        RolePermissionAddChildReq.ChildItem child = new RolePermissionAddChildReq.ChildItem(
            "DATA", "report:sales", "default", "VIEW", ScopeMode.INSTANCE, false, null);
        PermissionGrantDomainService.GrantCheckKey key =
            new PermissionGrantDomainService.GrantCheckKey(
                "DATA", "report:sales", "default", "VIEW", false);
        when(permissionGrantDomainService.checkCanGrant(
            eq(1L), eq(10L), eq(Set.of(key)), eq(null)))
            .thenReturn(Map.of(
                "DATA:report:sales:default:VIEW:SPECIFIC",
                new PermissionGrantDomainService.GrantCheckResult(false, "NO_DELEGABLE_PERMISSION")));

        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(10L);

            BizException exception = assertThrows(BizException.class, () ->
                service.addChildren(1L, new RolePermissionAddChildReq(10L, List.of(child))));

            assertEquals(20040, exception.getErrorCode());
            verifyNoInteractions(typeResolutionService);
        }
    }
}
