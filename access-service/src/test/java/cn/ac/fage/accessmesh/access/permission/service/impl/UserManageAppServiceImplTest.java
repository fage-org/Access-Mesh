package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.access.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.access.permission.dto.req.UserRoleBatchRevokeReq;
import cn.ac.fage.accessmesh.access.permission.entity.UserRole;
import cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.access.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.permission.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.AbstractUserMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.UserRoleMapper;
import cn.ac.fage.accessmesh.access.permission.service.domain.AuditDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.DomainClassifyService;
import cn.ac.fage.accessmesh.access.permission.service.domain.SubjectDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.access.permission.util.OperatorContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserManageAppServiceImplTest {

    @Mock private AbstractUserMapper abstractUserMapper;
    @Mock private UserRoleMapper userRoleMapper;
    @Mock private AbstractRoleMapper abstractRoleMapper;
    @Mock private SubjectDomainService subjectDomainService;
    @Mock private TypeResolutionService typeResolutionService;
    @Mock private DomainClassifyService domainClassifyService;
    @Mock private AuditDomainService auditDomainService;
    @Mock private PermQueryEngine engine;

    private UserManageAppServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserManageAppServiceImpl(
            abstractUserMapper,
            userRoleMapper,
            abstractRoleMapper,
            subjectDomainService,
            typeResolutionService,
            domainClassifyService,
            auditDomainService,
            new cn.ac.fage.accessmesh.access.permission.service.domain.LocalProjectionGuard(),
            new ObjectMapper(),
            engine
        );
    }

    @Test
    void shouldThrowUserRoleRelationNotFoundWhenRelationMissingDuringBatchRevoke() {
        UserRoleBatchRevokeReq req = new UserRoleBatchRevokeReq(List.of(
            new UserRoleBatchRevokeReq.RevokeItem("user", "u-1", "default", "role", "r-1", null)
        ));

        when(typeResolutionService.batchResolveRoleIds(eq(1L), eq("role"), eq(Set.of("r-1")), eq("default")))
            .thenReturn(Map.of("r-1", 10L));
        when(typeResolutionService.batchResolveUserIds(eq(1L), eq("user"), eq(Set.of("u-1"))))
            .thenReturn(Map.of("u-1", 20L));

        try (MockedStatic<OperatorContext> operatorContext = org.mockito.Mockito.mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.getDeniedIds(eq(1L), eq(100L), eq(ResourceTypeCode.ROLE), eq(Set.of(10L)), eq(OperationCodeConstants.MANAGE)))
                .thenReturn(Set.of());
            when(abstractRoleMapper.selectValidByIds(eq(1L), eq(Set.of(10L)))).thenReturn(List.of());
            when(userRoleMapper.selectValidByUserIdsTypeAndTargetIds(eq(1L), eq(Set.of(20L)), eq(ResourceTypeCode.ROLE), eq(Set.of(10L))))
                .thenReturn(List.<UserRole>of());

            BizException exception = assertThrows(BizException.class, () -> service.revokeRolesBatch(1L, req));

            assertEquals(PermissionErrorCode.USER_ROLE_RELATION_NOT_FOUND.getCode(), exception.getErrorCode());
        }
    }
}