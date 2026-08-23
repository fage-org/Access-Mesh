package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.access.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.access.permission.enums.DomainQueryMode;
import cn.ac.fage.accessmesh.access.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.permission.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.access.permission.service.domain.AuditDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.SubjectDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.DomainClassifyService;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleManageAppServiceImplTest {

    @Mock private AbstractRoleMapper abstractRoleMapper;
    @Mock private SubjectDomainService subjectDomainService;
    @Mock private TypeResolutionService typeResolutionService;
    @Mock private DomainClassifyService domainClassifyService;
    @Mock private AuditDomainService auditDomainService;
    @Mock private PermQueryEngine engine;

    private RoleManageAppServiceImpl service;

    @BeforeEach
    void setUp() {
        // 测试简化：投影主体 = 传入 operatorId（两套 ID 真实差异由 PermissionViewAppServiceImplTest 覆盖）
        lenient().when(engine.resolveOperatorSubjectId(anyLong(), anyLong()))
            .thenAnswer(inv -> inv.getArgument(1));
        service = new RoleManageAppServiceImpl(
            abstractRoleMapper,
            subjectDomainService,
            typeResolutionService,
            domainClassifyService,
            new ObjectMapper(),
            auditDomainService,
            new cn.ac.fage.accessmesh.access.permission.service.domain.LocalProjectionGuard(),
            engine
        );
    }

    @Test
    void shouldShortCircuitRoleTreeWhenDomainDoesNotCoverRoleType() {
        when(domainClassifyService.matchesTypeCode(1L, DomainQueryMode.GLOBAL_PLUS, "OPS", ResourceTypeCode.ROLE))
            .thenReturn(false);

        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.ROLE),
                isNull(), eq(OperationCodeConstants.VIEW))).thenReturn(true);

            assertEquals(List.of(), service.getRoleTree(1L, "OPS"));
        }
        verifyNoInteractions(abstractRoleMapper);
    }

    /** T-PERM-042（architecture §14.5）：角色树读接口补类型级 ROLE:VIEW 门禁。 */
    @Test
    void shouldRejectRoleTreeWithoutRoleViewPermission() {
        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.ROLE),
                isNull(), eq(OperationCodeConstants.VIEW))).thenReturn(false);

            assertThrows(SecurityException.class, () -> service.getRoleTree(1L, null));
        }
        verifyNoInteractions(abstractRoleMapper);
        verifyNoInteractions(domainClassifyService);
    }
}
