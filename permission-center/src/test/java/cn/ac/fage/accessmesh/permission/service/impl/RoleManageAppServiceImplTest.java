package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.permission.enums.DomainQueryMode;
import cn.ac.fage.accessmesh.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.permission.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.permission.service.domain.AuditDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.SubjectDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.DomainClassifyService;
import cn.ac.fage.accessmesh.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.permission.service.domain.impl.PermQueryEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        service = new RoleManageAppServiceImpl(
            abstractRoleMapper,
            subjectDomainService,
            typeResolutionService,
            domainClassifyService,
            new ObjectMapper(),
            auditDomainService,
            engine
        );
    }

    @Test
    void shouldShortCircuitRoleTreeWhenDomainDoesNotCoverRoleType() {
        when(domainClassifyService.matchesTypeCode(1L, DomainQueryMode.GLOBAL_PLUS, "OPS", ResourceTypeCode.ROLE))
            .thenReturn(false);

        assertEquals(List.of(), service.getRoleTree(1L, "OPS"));
        verifyNoInteractions(abstractRoleMapper);
    }
}
