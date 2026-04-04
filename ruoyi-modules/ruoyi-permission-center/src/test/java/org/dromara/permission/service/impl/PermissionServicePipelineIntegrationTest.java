package org.dromara.permission.service.impl;

import org.dromara.permission.constant.PermissionConstants;
import org.dromara.permission.domain.PcAbstractRole;
import org.dromara.permission.domain.PcOperationPermission;
import org.dromara.permission.domain.PcPermissionCondition;
import org.dromara.permission.domain.PcPermissionVersion;
import org.dromara.permission.domain.PcResourceEntity;
import org.dromara.permission.domain.PcRoleResourcePermission;
import org.dromara.permission.domain.PcUserRole;
import org.dromara.permission.mapper.PcAbstractRoleMapper;
import org.dromara.permission.mapper.PcAbstractUserMapper;
import org.dromara.permission.mapper.PcDomainRelationConfigMapper;
import org.dromara.permission.mapper.PcDomainScopeConfigMapper;
import org.dromara.permission.mapper.PcOperationPermissionMapper;
import org.dromara.permission.mapper.PcPermissionChangeLogMapper;
import org.dromara.permission.mapper.PcPermissionConditionMapper;
import org.dromara.permission.mapper.PcPermissionConflictRuleMapper;
import org.dromara.permission.mapper.PcPermissionVersionMapper;
import org.dromara.permission.mapper.PcResourceDependencyMapper;
import org.dromara.permission.mapper.PcResourceEntityMapper;
import org.dromara.permission.mapper.PcRoleResourcePermissionMapper;
import org.dromara.permission.mapper.PcUserRoleMapper;
import org.dromara.permission.model.permission.PermissionCheckRequest;
import org.dromara.permission.model.permission.PermissionVersionQueryRequest;
import org.dromara.permission.model.permission.SnapshotRequest;
import org.dromara.permission.service.ChangeLogService;
import org.dromara.permission.service.OperationInheritanceService;
import org.dromara.permission.service.PermissionVersionService;
import org.dromara.permission.service.RoleResolverService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class PermissionServicePipelineIntegrationTest {

    @Mock private PcUserRoleMapper userRoleMapper;
    @Mock private PcAbstractRoleMapper abstractRoleMapper;
    @Mock private PcAbstractUserMapper abstractUserMapper;
    @Mock private PcResourceEntityMapper resourceEntityMapper;
    @Mock private PcOperationPermissionMapper operationPermissionMapper;
    @Mock private PcRoleResourcePermissionMapper roleResourcePermissionMapper;
    @Mock private PcPermissionConditionMapper permissionConditionMapper;
    @Mock private PcPermissionConflictRuleMapper permissionConflictRuleMapper;
    @Mock private PcResourceDependencyMapper resourceDependencyMapper;
    @Mock private PcPermissionChangeLogMapper changeLogMapper;
    @Mock private PcPermissionVersionMapper permissionVersionMapper;
    @Mock private PcDomainScopeConfigMapper domainScopeConfigMapper;
    @Mock private PcDomainRelationConfigMapper domainRelationConfigMapper;

    private PermissionServiceImpl permissionService;

    @BeforeEach
    void setUp() {
        RoleResolverService roleResolverService = new RoleResolverServiceImpl(userRoleMapper, abstractRoleMapper);
        OperationInheritanceService operationInheritanceService = new OperationInheritanceServiceImpl();
        PermissionVersionService permissionVersionService = new PermissionVersionServiceImpl(permissionVersionMapper);
        ChangeLogService changeLogService = new ChangeLogServiceImpl(new PermissionChangeLogServiceImpl(changeLogMapper));
        permissionService = new PermissionServiceImpl(
            roleResolverService,
            operationInheritanceService,
            new DomainScopeValidatorImpl(domainScopeConfigMapper, domainRelationConfigMapper),
            changeLogService,
            permissionVersionService,
            resourceEntityMapper,
            operationPermissionMapper,
            roleResourcePermissionMapper,
            permissionConditionMapper,
            permissionConflictRuleMapper,
            resourceDependencyMapper,
            abstractRoleMapper,
            abstractUserMapper,
            userRoleMapper
        );
    }

    @Test
    void check_buildSnapshot_queryVersion_followSameKernelPipeline() {
        Long tenantId = 1L;
        Long userId = 100L;
        Long roleId = 200L;
        Long resourceId = 300L;
        Long operationId = 400L;
        Long conditionId = 500L;

        when(userRoleMapper.selectEffectiveByTenantAndUser(any(), any(), any()))
            .thenReturn(List.of(userRole(userId, roleId)));
        when(abstractRoleMapper.selectList(any())).thenReturn(List.of(role(roleId)));
        when(resourceEntityMapper.selectOne(any())).thenReturn(resource(resourceId));
        when(operationPermissionMapper.selectOne(any())).thenReturn(operation(operationId));
        when(roleResourcePermissionMapper.selectList(any()))
            .thenReturn(List.of(grant(roleId, resourceId, operationId, conditionId)))
            .thenReturn(List.of(grant(roleId, resourceId, operationId, conditionId)));
        when(permissionConditionMapper.selectBatchIds(any()))
            .thenReturn(List.of(condition(conditionId, "WORKDAY_ONLY")))
            .thenReturn(List.of(condition(conditionId, "WORKDAY_ONLY")));
        when(permissionConflictRuleMapper.selectByTenantAndResourceType(tenantId, 1)).thenReturn(Collections.emptyList());
        when(resourceDependencyMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(resourceEntityMapper.selectList(any())).thenReturn(List.of(resource(resourceId)));
        when(operationPermissionMapper.selectList(any())).thenReturn(List.of(operation(operationId)));
        when(permissionVersionMapper.selectLatestByTenant(tenantId)).thenReturn(version(tenantId, 7L));

        PermissionCheckRequest checkRequest = new PermissionCheckRequest();
        checkRequest.setTenantId(tenantId);
        checkRequest.setAbstractUserId(userId);
        checkRequest.setResourceEntityId(resourceId);
        checkRequest.setOperationPermissionId(operationId);
        checkRequest.setContext(Map.of("condition:WORKDAY_ONLY", true));
        var checkResult = permissionService.check(checkRequest);

        SnapshotRequest snapshotRequest = new SnapshotRequest();
        snapshotRequest.setTenantId(tenantId);
        snapshotRequest.setAbstractUserId(userId);
        snapshotRequest.setIncludeConditional(true);
        var snapshot = permissionService.buildSnapshot(snapshotRequest);

        PermissionVersionQueryRequest versionRequest = new PermissionVersionQueryRequest();
        versionRequest.setTenantId(tenantId);
        var version = permissionService.queryVersion(versionRequest);

        assertTrue(checkResult.isGranted());
        assertEquals(1, snapshot.getEntries().size());
        assertEquals("1-v7", version.getVersionToken());
    }

    private PcUserRole userRole(Long userId, Long roleId) {
        PcUserRole userRole = new PcUserRole();
        userRole.setId(1L);
        userRole.setTenantId(1L);
        userRole.setAbstractUserId(userId);
        userRole.setAbstractRoleId(roleId);
        userRole.setDeleteFlag(PermissionConstants.NOT_DELETED);
        userRole.setValidFrom(LocalDateTime.now().minusDays(1));
        userRole.setValidTo(LocalDateTime.now().plusDays(1));
        return userRole;
    }

    private PcAbstractRole role(Long roleId) {
        PcAbstractRole role = new PcAbstractRole();
        role.setId(roleId);
        role.setTenantId(1L);
        role.setRoleType(1);
        role.setDeleteFlag(PermissionConstants.NOT_DELETED);
        return role;
    }

    private PcResourceEntity resource(Long resourceId) {
        PcResourceEntity resource = new PcResourceEntity();
        resource.setId(resourceId);
        resource.setTenantId(1L);
        resource.setCode("RES-" + resourceId);
        resource.setResourceType(1);
        resource.setPath("/" + resourceId);
        resource.setDeleteFlag(PermissionConstants.NOT_DELETED);
        return resource;
    }

    private PcOperationPermission operation(Long operationId) {
        PcOperationPermission operation = new PcOperationPermission();
        operation.setId(operationId);
        operation.setTenantId(1L);
        operation.setCode("ACCESS");
        operation.setResourceType(1);
        operation.setBinaryBit(1L);
        operation.setInheritMask(0L);
        operation.setDeleteFlag(PermissionConstants.NOT_DELETED);
        return operation;
    }

    private PcRoleResourcePermission grant(Long roleId, Long resourceId, Long operationId, Long conditionId) {
        PcRoleResourcePermission grant = new PcRoleResourcePermission();
        grant.setId(1L);
        grant.setTenantId(1L);
        grant.setAbstractRoleId(roleId);
        grant.setResourceEntityId(resourceId);
        grant.setOperationPermissionId(operationId);
        grant.setConditionId(conditionId);
        grant.setDeleteFlag(PermissionConstants.NOT_DELETED);
        return grant;
    }

    private PcPermissionCondition condition(Long conditionId, String expression) {
        PcPermissionCondition condition = new PcPermissionCondition();
        condition.setId(conditionId);
        condition.setTenantId(1L);
        condition.setCode(expression);
        condition.setConditionSource(PermissionConstants.CONDITION_SOURCE_PRESET);
        condition.setExpression(expression);
        condition.setStatus(PermissionConstants.CONDITION_STATUS_APPROVED);
        condition.setDeleteFlag(PermissionConstants.NOT_DELETED);
        return condition;
    }

    private PcPermissionVersion version(Long tenantId, Long versionNo) {
        PcPermissionVersion version = new PcPermissionVersion();
        version.setTenantId(tenantId);
        version.setVersionNo(versionNo);
        version.setTriggerEntityType("role_resource_permission");
        version.setTriggerEntityId(1L);
        version.setRemark("test");
        version.setUpdatedAt(LocalDateTime.now());
        return version;
    }
}
