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
import cn.ac.fage.accessmesh.permission.service.domain.AuditDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionGrantDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionVersionDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.SubjectDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.permission.service.domain.DomainClassifyService;
import cn.ac.fage.accessmesh.permission.service.domain.impl.PermQueryEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
    /** 权限版本领域服务Mock */
    @Mock private PermissionVersionDomainService permissionVersionDomainService;
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
            rolePermMapper, permissionGrantDomainService, permissionVersionDomainService,
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
            10L, List.of(new RolePermissionAddChildReq.ChildItem("SER", "x", "default", "VIEW", false, null, null))
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
            10L, List.of(new RolePermissionAddChildReq.ChildItem("DATA", null, "default", "DATA_READ", false, null, null))
        );

        assertThrows(BizException.class, () -> service.addChildren(1L, req));
    }
}
