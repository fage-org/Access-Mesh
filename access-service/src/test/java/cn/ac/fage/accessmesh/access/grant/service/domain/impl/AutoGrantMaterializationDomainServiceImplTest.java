package cn.ac.fage.accessmesh.access.grant.service.domain.impl;

import cn.ac.fage.accessmesh.access.audit.service.domain.AuditDomainService;
import cn.ac.fage.accessmesh.access.engine.core.SubjectDomainService;
import cn.ac.fage.accessmesh.access.grant.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.access.grant.service.domain.AutoGrantDerivation;
import cn.ac.fage.accessmesh.access.resource.service.domain.DependencyCompilationDomainService;
import cn.ac.fage.accessmesh.access.resource.service.domain.ResourceEntityDomainService;
import cn.ac.fage.accessmesh.access.role.entity.AbstractRole;
import cn.ac.fage.accessmesh.access.rule.service.domain.PermissionConditionDomainService;
import cn.ac.fage.accessmesh.access.type.service.domain.OperationPermissionDomainService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 自动授权物化实现的装载分批回归锁：受影响角色集合与 manifest 条目数无关（一条声明×
 * 一个源资源即可波及全租户持有角色），角色维度的有效角色与授权行装载必须按
 * {@code SqlBatches}（500/批）分批防 IN 参数上限，且合批结果覆盖完整集合、不退化为整批直传。
 */
@ExtendWith(MockitoExtension.class)
class AutoGrantMaterializationDomainServiceImplTest {

    private static final Long TENANT = 1L;

    @Mock private RoleResourcePermissionMapper rolePermissionMapper;
    @Mock private AutoGrantDerivation derivation;
    @Mock private DependencyCompilationDomainService compilation;
    @Mock private SubjectDomainService subjectDomainService;
    @Mock private ResourceEntityDomainService resourceEntities;
    @Mock private OperationPermissionDomainService operations;
    @Mock private PermissionConditionDomainService conditionDomainService;
    @Mock private AuditDomainService auditDomainService;

    private AutoGrantMaterializationDomainServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AutoGrantMaterializationDomainServiceImpl(rolePermissionMapper, derivation,
            compilation, subjectDomainService, resourceEntities, operations,
            conditionDomainService, auditDomainService, new ObjectMapper());
    }

    @Test
    void shouldLoadRoleDimensionInSqlBatches() {
        List<Long> roleIds = new ArrayList<>();
        for (long id = 1; id <= 1200; id++) {
            roleIds.add(id);
        }
        when(subjectDomainService.selectValidRolesByIds(eq(TENANT), anySet())).thenAnswer(invocation -> {
            Set<Long> requested = invocation.getArgument(1);
            return requested.stream().map(AutoGrantMaterializationDomainServiceImplTest::role).toList();
        });
        when(rolePermissionMapper.selectValidByRoleIds(eq(TENANT), anySet())).thenReturn(List.of());
        when(derivation.derive(any(), any(), any(), any()))
            .thenReturn(new AutoGrantDerivation.Result(List.of(), Map.of()));

        assertThat(service.recompute(TENANT, roleIds, null)).isEmpty();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<Long>> validCaptor = ArgumentCaptor.forClass(Set.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<Long>> rowsCaptor = ArgumentCaptor.forClass(Set.class);
        verify(subjectDomainService, times(3)).selectValidRolesByIds(eq(TENANT), validCaptor.capture());
        verify(rolePermissionMapper, times(3)).selectValidByRoleIds(eq(TENANT), rowsCaptor.capture());
        assertThat(validCaptor.getAllValues())
            .allSatisfy(batch -> assertThat(batch).hasSizeLessThanOrEqualTo(500));
        assertThat(rowsCaptor.getAllValues())
            .allSatisfy(batch -> assertThat(batch).hasSizeLessThanOrEqualTo(500));
        assertThat(validCaptor.getAllValues().stream().flatMap(Set::stream))
            .containsExactlyInAnyOrderElementsOf(roleIds);
        assertThat(rowsCaptor.getAllValues().stream().flatMap(Set::stream))
            .containsExactlyInAnyOrderElementsOf(roleIds);
    }

    private static AbstractRole role(long id) {
        AbstractRole role = new AbstractRole();
        role.setId(id);
        role.setTenantId(TENANT);
        return role;
    }
}
