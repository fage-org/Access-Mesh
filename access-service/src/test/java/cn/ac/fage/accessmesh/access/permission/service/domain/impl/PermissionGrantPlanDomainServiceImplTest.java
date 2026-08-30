package cn.ac.fage.accessmesh.access.permission.service.domain.impl;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.access.permission.dto.req.ApplyGrantPlanReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceResolveKey;
import cn.ac.fage.accessmesh.access.permission.entity.DomainConfig;
import cn.ac.fage.accessmesh.access.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.access.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.access.permission.entity.RoleResourcePermission;
import cn.ac.fage.accessmesh.access.permission.mapper.DomainConfigMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.PermissionConditionMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.access.permission.service.domain.DomainClassifyService;
import cn.ac.fage.accessmesh.access.permission.service.domain.PermissionGrantDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.PermissionGrantPlanDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.perm.common.enums.ScopeMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T-PERM-034 授权计划预检测试矩阵（非笛卡尔积，按适用命令覆盖）：
 * 成功路径 6 / 子权限属性反例（20043）/ 不变量反例（互斥/重复/存在性/AUTO_DEP 只读/
 * 父归属）/ SUB_PERM 四格（INSTANCE/ALL × 具体域/全局域）/ 查询次数断言 /
 * SubPermissionPolicy 判定优先级 0-6。
 */
@ExtendWith(MockitoExtension.class)
class PermissionGrantPlanDomainServiceImplTest {

    private static final Long TENANT = 1L;
    private static final Long SUBJECT = 10L;
    private static final Long ROLE = 20L;

    @Mock private TypeResolutionService typeResolutionService;
    @Mock private DomainClassifyService domainClassifyService;
    @Mock private PermissionGrantDomainService permissionGrantDomainService;
    @Mock private RoleResourcePermissionMapper rolePermissionMapper;
    @Mock private ResourceEntityMapper resourceEntityMapper;
    @Mock private OperationPermissionMapper operationPermissionMapper;
    @Mock private PermissionConditionMapper permissionConditionMapper;
    @Mock private DomainConfigMapper domainConfigMapper;

    private PermissionGrantPlanDomainServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PermissionGrantPlanDomainServiceImpl(
            typeResolutionService, domainClassifyService, permissionGrantDomainService,
            rolePermissionMapper, resourceEntityMapper, operationPermissionMapper,
            permissionConditionMapper, domainConfigMapper, new ObjectMapper());
    }

    // ========== 辅助 ==========

    private static ApplyGrantPlanReq.GrantRecordKey key(String resourceCode, ScopeMode scopeMode,
                                                        String conditionCode, Boolean canGrant) {
        return new ApplyGrantPlanReq.GrantRecordKey(
            "DATA", resourceCode, scopeMode == ScopeMode.ALL ? null : "default",
            "VIEW", scopeMode, conditionCode, canGrant);
    }

    private static RoleResourcePermission existing(long id, Long dependOn, String grantSource) {
        RoleResourcePermission permission = new RoleResourcePermission();
        permission.setId(id);
        permission.setTenantId(TENANT);
        permission.setAbstractRoleId(ROLE);
        permission.setResourceType(4);
        permission.setResourceEntityId(101L);
        permission.setGrantedBits(2L);
        permission.setScopeAll(false);
        permission.setDependOn(dependOn);
        permission.setGrantSource(grantSource);
        permission.setCanGrant(false);
        permission.setDeleteFlag(0L);
        return permission;
    }

    private static ResourceEntity resource(long id, String code) {
        ResourceEntity entity = new ResourceEntity();
        entity.setId(id);
        entity.setTenantId(TENANT);
        entity.setCode(code);
        entity.setCodeType("default");
        return entity;
    }

    private void stubCreateBase() {
        when(rolePermissionMapper.selectValidByRoleId(TENANT, ROLE)).thenReturn(List.of());
        when(typeResolutionService.batchResolveTypeValues(TENANT, "resource_type", java.util.Set.of("DATA")))
            .thenReturn(Map.of("DATA", 4));
        when(typeResolutionService.batchResolveResourceIds(eq(TENANT), any()))
            .thenReturn(Map.of(
                new ResourceResolveKey("DATA", "report:sales", "default", null), 101L,
                new ResourceResolveKey("DATA", "city:shanghai", "default", null), 102L));
        when(operationPermissionMapper.selectByTenantAndResourceType(TENANT, null))
            .thenReturn(List.of(dataView()));
    }

    /** DATA(4) 类型的 VIEW 专属操作定义（全局操作概念已退役，类型轨唯一形态） */
    private static OperationPermission dataView() {
        OperationPermission view = new OperationPermission();
        view.setId(9L);
        view.setResourceType(4);
        view.setCode("VIEW");
        view.setBinaryBit(2L);
        view.setInheritMask(0L);
        return view;
    }

    private void stubDelegationAllowed() {
        when(permissionGrantDomainService.checkCanGrant(eq(TENANT), eq(SUBJECT), any(), eq(null)))
            .thenAnswer(invocation -> {
                java.util.Set<PermissionGrantDomainService.GrantCheckKey> keys = invocation.getArgument(2);
                return keys.stream().collect(java.util.stream.Collectors.toMap(
                    this::grantKey,
                    k -> new PermissionGrantDomainService.GrantCheckResult(true, null)));
            });
    }

    private String grantKey(PermissionGrantDomainService.GrantCheckKey key) {
        return String.format("%s:%s:%s:%s:%s",
            key.resourceTypeCode(),
            key.resourceCode() == null ? "*" : key.resourceCode(),
            key.codeType() == null ? "*" : key.codeType(),
            key.operationCode(),
            key.scopeAll() ? "ALL" : "SPECIFIC");
    }

    private void stubUpdateRemoveBase(RoleResourcePermission... existingRows) {
        when(rolePermissionMapper.selectValidByRoleId(TENANT, ROLE)).thenReturn(List.of(existingRows));
        when(typeResolutionService.batchResolveTypeValues(eq(TENANT), eq("resource_type"), anySet()))
            .thenReturn(Map.of("DATA", 4));
        when(typeResolutionService.batchResolveTypeCodes(eq(TENANT), eq("resource_type"), anySet()))
            .thenReturn(Map.of(4, "DATA"));
        ResourceEntity resource = new ResourceEntity();
        resource.setId(101L);
        resource.setTenantId(TENANT);
        resource.setCode("report:sales");
        resource.setCodeType("default");
        when(resourceEntityMapper.selectValidByIds(eq(TENANT), anySet()))
            .thenReturn(List.of(resource));
        when(operationPermissionMapper.selectByTenantAndResourceType(TENANT, null))
            .thenReturn(List.of(dataView()));
    }

    private void stubSubPermConfig(String extra, Long domainId) {
        DomainConfig subPerm = new DomainConfig();
        subPerm.setBizDomainId(domainId);
        subPerm.setConfigType("SUB_PERM");
        subPerm.setExtra(extra);
        when(domainClassifyService.findDomainIdsByTypeCodes(TENANT, java.util.Set.of("DATA")))
            .thenReturn(Map.of("DATA", domainId));
        when(domainConfigMapper.selectByTenantId(TENANT)).thenReturn(List.of(subPerm));
    }

    private ApplyGrantPlanReq.GrantPlan nestedCreatePlan() {
        return new ApplyGrantPlanReq.GrantPlan(
            List.of(new ApplyGrantPlanReq.CreateItem(
                key("report:sales", ScopeMode.INSTANCE, null, false), null,
                List.of(key("city:shanghai", ScopeMode.INSTANCE, null, false)))),
            List.of(), List.of());
    }

    // ========== 成功路径 ==========

    @Nested
    class SuccessPaths {

        @Test
        void shouldPrepareMainPermissionCreate() {
            stubCreateBase();
            stubDelegationAllowed();

            PermissionGrantPlanDomainService.PreparedGrantPlan prepared = service.prevalidate(
                TENANT, SUBJECT, ROLE, null,
                new ApplyGrantPlanReq.GrantPlan(List.of(new ApplyGrantPlanReq.CreateItem(
                    key("report:sales", ScopeMode.INSTANCE, null, false), null, List.of())),
                    List.of(), List.of()));

            assertEquals(1, prepared.creates().size());
            assertTrue(prepared.creates().get(0).children().isEmpty());
            assertEquals(1, prepared.auditKeys().size());
            assertEquals("ADD", prepared.auditKeys().get(0).changeType());
            assertEquals("report:sales", prepared.auditKeys().get(0).resourceCode());
            assertEquals(ScopeMode.INSTANCE, prepared.auditKeys().get(0).scopeMode());
        }

        @Test
        void shouldPrepareNestedChildWithExplicitWildcardSubPermConfig() {
            stubCreateBase();
            stubDelegationAllowed();
            stubSubPermConfig("*", 7L);

            PermissionGrantPlanDomainService.PreparedGrantPlan prepared = service.prevalidate(
                TENANT, SUBJECT, ROLE, null, nestedCreatePlan());

            assertEquals(1, prepared.creates().size());
            assertEquals(1, prepared.creates().get(0).children().size());
            assertEquals(2, prepared.delegationKeys().size());
            assertEquals(2, prepared.auditKeys().size());
        }

        @Test
        void shouldPrepareChildCreateReferencingExistingParent() {
            when(rolePermissionMapper.selectValidByRoleId(TENANT, ROLE))
                .thenReturn(List.of(existing(5L, null, "MANUAL")));
            when(typeResolutionService.batchResolveTypeValues(eq(TENANT), eq("resource_type"), anySet()))
                .thenReturn(Map.of("DATA", 4));
            when(typeResolutionService.batchResolveTypeCodes(eq(TENANT), eq("resource_type"), anySet()))
                .thenReturn(Map.of(4, "DATA"));
            when(typeResolutionService.batchResolveResourceIds(eq(TENANT), any()))
                .thenReturn(Map.of(new ResourceResolveKey("DATA", "city:shanghai", "default", null), 102L));
            when(operationPermissionMapper.selectByTenantAndResourceType(TENANT, null))
                .thenReturn(List.of(dataView()));
            stubDelegationAllowed();
            stubSubPermConfig("*", 7L);

            PermissionGrantPlanDomainService.PreparedGrantPlan prepared = service.prevalidate(
                TENANT, SUBJECT, ROLE, null,
                new ApplyGrantPlanReq.GrantPlan(List.of(new ApplyGrantPlanReq.CreateItem(
                    key("city:shanghai", ScopeMode.INSTANCE, null, false), 5L, List.of())),
                    List.of(), List.of()));

            assertEquals(5L, prepared.creates().get(0).permission().getDependOn());
            assertEquals(1, prepared.auditKeys().size());
            assertEquals("ADD", prepared.auditKeys().get(0).changeType());
        }

        @Test
        void shouldPrepareMainPermissionUpdateWithCanGrantChange() {
            stubUpdateRemoveBase(existing(5L, null, "MANUAL"));
            stubDelegationAllowed();

            PermissionGrantPlanDomainService.PreparedGrantPlan prepared = service.prevalidate(
                TENANT, SUBJECT, ROLE, null,
                new ApplyGrantPlanReq.GrantPlan(List.of(),
                    List.of(new ApplyGrantPlanReq.UpdateItem(5L, Boolean.TRUE, null)), List.of()));

            assertEquals(1, prepared.updates().size());
            assertEquals(1, prepared.auditKeys().size());
            assertEquals("UPDATE", prepared.auditKeys().get(0).changeType());
            assertEquals("DATA", prepared.auditKeys().get(0).resourceTypeCode());
            assertEquals("report:sales", prepared.auditKeys().get(0).resourceCode());
            assertEquals("VIEW", prepared.auditKeys().get(0).operationCode());
        }

        @Test
        void shouldPrepareChildRemoveWithBusinessKeySnapshot() {
            stubUpdateRemoveBase(existing(6L, 5L, "MANUAL"));

            PermissionGrantPlanDomainService.PreparedGrantPlan prepared = service.prevalidate(
                TENANT, SUBJECT, ROLE, null,
                new ApplyGrantPlanReq.GrantPlan(List.of(), List.of(), List.of(6L)));

            assertEquals(List.of(6L), prepared.removes());
            assertEquals(1, prepared.auditKeys().size());
            // removes 业务键快照在预检期装配（行随后被软删，事后不可回查）
            assertEquals("REMOVE", prepared.auditKeys().get(0).changeType());
            assertEquals("report:sales", prepared.auditKeys().get(0).resourceCode());
            assertEquals("VIEW", prepared.auditKeys().get(0).operationCode());
        }

        @Test
        void shouldSnapshotCascadedChildrenOnMainPermissionRemove() {
            when(rolePermissionMapper.selectValidByRoleId(TENANT, ROLE))
                .thenReturn(List.of(existing(5L, null, "MANUAL")));
            when(typeResolutionService.batchResolveTypeValues(eq(TENANT), eq("resource_type"), anySet()))
                .thenReturn(Map.of("DATA", 4));
            when(typeResolutionService.batchResolveTypeCodes(eq(TENANT), eq("resource_type"), anySet()))
                .thenReturn(Map.of(4, "DATA"));
            when(resourceEntityMapper.selectValidByIds(eq(TENANT), anySet()))
                .thenReturn(List.of(resource(101L, "report:sales"), resource(102L, "city:shanghai")));
            when(operationPermissionMapper.selectByTenantAndResourceType(TENANT, null))
                .thenReturn(List.of(dataView()));
            RoleResourcePermission cascadedChild = existing(6L, 5L, "MANUAL");
            cascadedChild.setResourceEntityId(102L);
            when(rolePermissionMapper.selectValidByDependOns(TENANT, java.util.Set.of(5L)))
                .thenReturn(List.of(cascadedChild));

            PermissionGrantPlanDomainService.PreparedGrantPlan prepared = service.prevalidate(
                TENANT, SUBJECT, ROLE, null,
                new ApplyGrantPlanReq.GrantPlan(List.of(), List.of(), List.of(5L)));

            assertEquals(List.of(5L), prepared.removes());
            // 主权限 + 级联删除的子权限都进 diff_snapshot（实际被删除的行可按业务键检索本次变更）
            assertEquals(2, prepared.auditKeys().size());
            assertTrue(prepared.auditKeys().stream().allMatch(key -> "REMOVE".equals(key.changeType())));
            assertTrue(prepared.auditKeys().stream().anyMatch(key -> "report:sales".equals(key.resourceCode())));
            assertTrue(prepared.auditKeys().stream().anyMatch(key -> "city:shanghai".equals(key.resourceCode())));
            verify(rolePermissionMapper).selectValidByDependOns(TENANT, java.util.Set.of(5L));
        }

        @Test
        void shouldNotDuplicateChildAlreadyRemovedExplicitly() {
            when(rolePermissionMapper.selectValidByRoleId(TENANT, ROLE))
                .thenReturn(List.of(existing(5L, null, "MANUAL"), existing(6L, 5L, "MANUAL")));
            when(typeResolutionService.batchResolveTypeValues(eq(TENANT), eq("resource_type"), anySet()))
                .thenReturn(Map.of("DATA", 4));
            when(typeResolutionService.batchResolveTypeCodes(eq(TENANT), eq("resource_type"), anySet()))
                .thenReturn(Map.of(4, "DATA"));
            when(resourceEntityMapper.selectValidByIds(eq(TENANT), anySet()))
                .thenReturn(List.of(resource(101L, "report:sales")));
            when(operationPermissionMapper.selectByTenantAndResourceType(TENANT, null))
                .thenReturn(List.of(dataView()));
            // 级联查询同样命中子权限 6（depend_on=5），但其已在显式 removes 中：业务键只快照一次
            when(rolePermissionMapper.selectValidByDependOns(TENANT, java.util.Set.of(5L, 6L)))
                .thenReturn(List.of(existing(6L, 5L, "MANUAL")));

            PermissionGrantPlanDomainService.PreparedGrantPlan prepared = service.prevalidate(
                TENANT, SUBJECT, ROLE, null,
                new ApplyGrantPlanReq.GrantPlan(List.of(), List.of(), List.of(5L, 6L)));

            assertEquals(2, prepared.auditKeys().size());
            assertTrue(prepared.auditKeys().stream().allMatch(key -> "REMOVE".equals(key.changeType())));
        }

        @Test
        void shouldCascadeSoftDeleteChildrenOnMainPermissionRemove() {
            PermissionGrantPlanDomainService.PreparedGrantPlan prepared =
                new PermissionGrantPlanDomainService.PreparedGrantPlan(
                    TENANT, ROLE, List.of(), List.of(), List.of(5L), java.util.Set.of(), List.of());
            when(rolePermissionMapper.softDeleteBatch(eq(TENANT), eq(List.of(5L)), any()))
                .thenReturn(1);

            service.apply(prepared);

            verify(rolePermissionMapper).cascadeSoftDeleteChildren(eq(TENANT), eq(List.of(5L)), any());
        }
    }

    // ========== 子权限属性系统不变量（20043，先于 20041） ==========

    @Nested
    class ChildAttributeInvariant {

        @Test
        void shouldRejectNestedChildCreateWithConditionCode() {
            when(rolePermissionMapper.selectValidByRoleId(TENANT, ROLE)).thenReturn(List.of());

            BizException exception = assertThrows(BizException.class, () -> service.prevalidate(
                TENANT, SUBJECT, ROLE, null,
                new ApplyGrantPlanReq.GrantPlan(List.of(new ApplyGrantPlanReq.CreateItem(
                    key("report:sales", ScopeMode.INSTANCE, null, false), null,
                    List.of(key("city:shanghai", ScopeMode.INSTANCE, "cond-1", false)))),
                    List.of(), List.of())));

            assertEquals(20043, exception.getErrorCode());
        }

        @Test
        void shouldRejectNestedChildCreateWithCanGrantTrue() {
            when(rolePermissionMapper.selectValidByRoleId(TENANT, ROLE)).thenReturn(List.of());

            BizException exception = assertThrows(BizException.class, () -> service.prevalidate(
                TENANT, SUBJECT, ROLE, null,
                new ApplyGrantPlanReq.GrantPlan(List.of(new ApplyGrantPlanReq.CreateItem(
                    key("report:sales", ScopeMode.INSTANCE, null, false), null,
                    List.of(key("city:shanghai", ScopeMode.INSTANCE, null, true)))),
                    List.of(), List.of())));

            assertEquals(20043, exception.getErrorCode());
        }

        @Test
        void shouldRejectParentAttachedChildCreateWithConditionCodeBeforeMainInvariant() {
            when(rolePermissionMapper.selectValidByRoleId(TENANT, ROLE))
                .thenReturn(List.of(existing(5L, null, "MANUAL")));

            // 同时携带 conditionCode + canGrant=true：子权限分类先行 -> 20043（而非主权限 20041）
            BizException exception = assertThrows(BizException.class, () -> service.prevalidate(
                TENANT, SUBJECT, ROLE, null,
                new ApplyGrantPlanReq.GrantPlan(List.of(new ApplyGrantPlanReq.CreateItem(
                    key("city:shanghai", ScopeMode.INSTANCE, "cond-1", true), 5L, List.of())),
                    List.of(), List.of())));

            assertEquals(20043, exception.getErrorCode());
        }

        @Test
        void shouldRejectUpdateTargetingChildPermissionEvenWhenChangeIsEmpty() {
            // 契约「子权限 update 一律 20043」：空变更（canGrant/conditionCode 均 null）
            // 亦不得被 VALIDATION_FAILED 抢占——20043 判定已前置于预检循环
            when(rolePermissionMapper.selectValidByRoleId(TENANT, ROLE))
                .thenReturn(List.of(existing(6L, 5L, "MANUAL")));

            BizException exception = assertThrows(BizException.class, () -> service.prevalidate(
                TENANT, SUBJECT, ROLE, null,
                new ApplyGrantPlanReq.GrantPlan(List.of(),
                    List.of(new ApplyGrantPlanReq.UpdateItem(6L, null, null)), List.of())));

            assertEquals(20043, exception.getErrorCode());
        }

        @Test
        void shouldRejectUpdateTargetingChildPermissionWhoseParentIsRemovedInSamePlan() {
            // 同款：父在 removes 中的子权限 update，20043 先于「父被删」拒绝路径
            when(rolePermissionMapper.selectValidByRoleId(TENANT, ROLE))
                .thenReturn(List.of(existing(5L, null, "MANUAL"), existing(6L, 5L, "MANUAL")));

            BizException exception = assertThrows(BizException.class, () -> service.prevalidate(
                TENANT, SUBJECT, ROLE, null,
                new ApplyGrantPlanReq.GrantPlan(List.of(),
                    List.of(new ApplyGrantPlanReq.UpdateItem(6L, Boolean.TRUE, null)), List.of(5L))));

            assertEquals(20043, exception.getErrorCode());
        }

        @Test
        void shouldRejectUpdateTargetingChildPermissionCanGrantOnly() {
            // 20043 已前置于预检循环，类型/资源解析不再触达——最小 stub
            when(rolePermissionMapper.selectValidByRoleId(TENANT, ROLE))
                .thenReturn(List.of(existing(6L, 5L, "MANUAL")));

            BizException exception = assertThrows(BizException.class, () -> service.prevalidate(
                TENANT, SUBJECT, ROLE, null,
                new ApplyGrantPlanReq.GrantPlan(List.of(),
                    List.of(new ApplyGrantPlanReq.UpdateItem(6L, Boolean.TRUE, null)), List.of())));

            assertEquals(20043, exception.getErrorCode());
        }

        @Test
        void shouldRejectUpdateTargetingChildPermissionConditionOnly() {
            when(rolePermissionMapper.selectValidByRoleId(TENANT, ROLE))
                .thenReturn(List.of(existing(6L, 5L, "MANUAL")));
            cn.ac.fage.accessmesh.access.permission.entity.PermissionCondition condition =
                new cn.ac.fage.accessmesh.access.permission.entity.PermissionCondition();
            condition.setId(3L);
            condition.setCode("cond-1");

            BizException exception = assertThrows(BizException.class, () -> service.prevalidate(
                TENANT, SUBJECT, ROLE, null,
                new ApplyGrantPlanReq.GrantPlan(List.of(),
                    List.of(new ApplyGrantPlanReq.UpdateItem(6L, null, "cond-1")), List.of())));

            assertEquals(20043, exception.getErrorCode());
        }
    }

    // ========== 不变量反例 ==========

    @Nested
    class InvariantViolations {

        @Test
        void shouldRejectUpdateAndRemoveOnSameId() {

            BizException exception = assertThrows(BizException.class, () -> service.prevalidate(
                TENANT, SUBJECT, ROLE, null,
                new ApplyGrantPlanReq.GrantPlan(List.of(),
                    List.of(new ApplyGrantPlanReq.UpdateItem(5L, Boolean.TRUE, null)), List.of(5L))));

            assertEquals(cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode
                .VALIDATION_FAILED.getCode(), exception.getErrorCode());
        }

        @Test
        void shouldRejectDuplicateRemoveIds() {
            BizException exception = assertThrows(BizException.class, () -> service.prevalidate(
                TENANT, SUBJECT, ROLE, null,
                new ApplyGrantPlanReq.GrantPlan(List.of(), List.of(), List.of(5L, 5L))));

            assertEquals(cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode
                .VALIDATION_FAILED.getCode(), exception.getErrorCode());
        }

        @Test
        void shouldRejectUpdateWhenPermissionMissing() {
            when(rolePermissionMapper.selectValidByRoleId(TENANT, ROLE)).thenReturn(List.of());

            BizException exception = assertThrows(BizException.class, () -> service.prevalidate(
                TENANT, SUBJECT, ROLE, null,
                new ApplyGrantPlanReq.GrantPlan(List.of(),
                    List.of(new ApplyGrantPlanReq.UpdateItem(99L, Boolean.TRUE, null)), List.of())));

            assertEquals(20036, exception.getErrorCode());
        }

        @Test
        void shouldRejectChildCreateWhenParentMissing() {
            when(rolePermissionMapper.selectValidByRoleId(TENANT, ROLE)).thenReturn(List.of());

            BizException exception = assertThrows(BizException.class, () -> service.prevalidate(
                TENANT, SUBJECT, ROLE, null,
                new ApplyGrantPlanReq.GrantPlan(List.of(new ApplyGrantPlanReq.CreateItem(
                    key("city:shanghai", ScopeMode.INSTANCE, null, false), 99L, List.of())),
                    List.of(), List.of())));

            assertEquals(20009, exception.getErrorCode());
        }

        @Test
        void shouldRejectUpdateOnAutoDepRecord() {
            when(rolePermissionMapper.selectValidByRoleId(TENANT, ROLE))
                .thenReturn(List.of(existing(5L, null, "AUTO_DEP")));

            BizException exception = assertThrows(BizException.class, () -> service.prevalidate(
                TENANT, SUBJECT, ROLE, null,
                new ApplyGrantPlanReq.GrantPlan(List.of(),
                    List.of(new ApplyGrantPlanReq.UpdateItem(5L, Boolean.TRUE, null)), List.of())));

            assertEquals(20034, exception.getErrorCode());
        }

        @Test
        void shouldRejectRemoveOnAutoDepRecord() {
            when(rolePermissionMapper.selectValidByRoleId(TENANT, ROLE))
                .thenReturn(List.of(existing(5L, null, "AUTO_DEP")));

            BizException exception = assertThrows(BizException.class, () -> service.prevalidate(
                TENANT, SUBJECT, ROLE, null,
                new ApplyGrantPlanReq.GrantPlan(List.of(), List.of(), List.of(5L))));

            assertEquals(20034, exception.getErrorCode());
        }

        @Test
        void shouldRejectChildCreateUnderAutoDepParent() {
            when(rolePermissionMapper.selectValidByRoleId(TENANT, ROLE))
                .thenReturn(List.of(existing(5L, null, "AUTO_DEP")));

            BizException exception = assertThrows(BizException.class, () -> service.prevalidate(
                TENANT, SUBJECT, ROLE, null,
                new ApplyGrantPlanReq.GrantPlan(List.of(new ApplyGrantPlanReq.CreateItem(
                    key("city:shanghai", ScopeMode.INSTANCE, null, false), 5L, List.of())),
                    List.of(), List.of())));

            assertEquals(20034, exception.getErrorCode());
        }

        @Test
        void shouldRejectRemoveWhenAffectedRowCountChanges() {
            PermissionGrantPlanDomainService.PreparedGrantPlan prepared =
                new PermissionGrantPlanDomainService.PreparedGrantPlan(
                    TENANT, ROLE, List.of(), List.of(), List.of(99L), java.util.Set.of(), List.of());
            when(rolePermissionMapper.softDeleteBatch(eq(TENANT), eq(List.of(99L)), any()))
                .thenReturn(0);

            BizException exception = assertThrows(BizException.class, () -> service.apply(prepared));

            assertEquals(20036, exception.getErrorCode());
        }

        @Test
        void shouldRejectPlanWhenOperatorCannotDelegate() {
            stubCreateBase();
            DomainConfig subPerm = new DomainConfig();
            subPerm.setBizDomainId(7L);
            subPerm.setConfigType("SUB_PERM");
            subPerm.setExtra("*");
            when(domainClassifyService.findDomainIdsByTypeCodes(TENANT, java.util.Set.of("DATA")))
                .thenReturn(Map.of("DATA", 7L));
            when(domainConfigMapper.selectByTenantId(TENANT)).thenReturn(List.of(subPerm));
            when(permissionGrantDomainService.checkCanGrant(eq(TENANT), eq(SUBJECT), any(), eq(null)))
                .thenAnswer(invocation -> {
                    java.util.Set<PermissionGrantDomainService.GrantCheckKey> keys = invocation.getArgument(2);
                    return keys.stream().collect(java.util.stream.Collectors.toMap(
                        key -> String.format("%s:%s:%s:%s:%s",
                            key.resourceTypeCode(), key.resourceCode(), key.codeType(),
                            key.operationCode(), key.scopeAll() ? "ALL" : "SPECIFIC"),
                        key -> new PermissionGrantDomainService.GrantCheckResult(false, "NO_DELEGABLE_PERMISSION")));
                });

            BizException exception = assertThrows(BizException.class, () ->
                service.prevalidate(TENANT, SUBJECT, ROLE, null, nestedCreatePlan()));

            assertEquals(20040, exception.getErrorCode());
        }

        @Test
        void shouldLoadOperationsOnceAndDelegateOncePerPlan() {
            // 固定批次数断言：整计划一次操作全量加载 + 一次委托批量校验（与类型数无关）
            stubCreateBase();
            stubDelegationAllowed();
            stubSubPermConfig("*", 7L);

            service.prevalidate(TENANT, SUBJECT, ROLE, null, nestedCreatePlan());

            verify(operationPermissionMapper, times(1)).selectByTenantAndResourceType(TENANT, null);
            verify(permissionGrantDomainService, times(1)).checkCanGrant(eq(TENANT), eq(SUBJECT), any(), eq(null));
        }
    }

    // ========== SUB_PERM 四格（INSTANCE/ALL × 具体域/全局域） ==========

    @Nested
    class SubPermFourGrid {

        @Test
        void shouldAllowChildCreateForAllScopeChildInSpecificDomain() {
            stubCreateBase();
            stubDelegationAllowed();
            stubSubPermConfig("*", 7L);

            PermissionGrantPlanDomainService.PreparedGrantPlan prepared = service.prevalidate(
                TENANT, SUBJECT, ROLE, null,
                new ApplyGrantPlanReq.GrantPlan(List.of(new ApplyGrantPlanReq.CreateItem(
                    key("report:sales", ScopeMode.INSTANCE, null, false), null,
                    List.of(key(null, ScopeMode.ALL, null, false)))),
                    List.of(), List.of()));

            assertEquals(1, prepared.creates().get(0).children().size());
            assertEquals(ScopeMode.ALL, prepared.auditKeys().get(1).scopeMode());
        }

        @Test
        void shouldAllowChildCreateForInstanceChildInGlobalDomain() {
            stubCreateBase();
            stubDelegationAllowed();
            // 全局域：父类型未被具体域认领 -> 域分类回退全局域（findDomainIds 语义）
            stubSubPermConfig("*", 99L);

            PermissionGrantPlanDomainService.PreparedGrantPlan prepared = service.prevalidate(
                TENANT, SUBJECT, ROLE, null, nestedCreatePlan());

            assertEquals(1, prepared.creates().get(0).children().size());
        }

        @Test
        void shouldFailClosedWhenSubPermConfigIsMissing() {
            stubCreateBase();
            when(domainClassifyService.findDomainIdsByTypeCodes(TENANT, java.util.Set.of("DATA")))
                .thenReturn(Map.of("DATA", 7L));
            when(domainConfigMapper.selectByTenantId(TENANT)).thenReturn(List.of());

            BizException exception = assertThrows(BizException.class, () ->
                service.prevalidate(TENANT, SUBJECT, ROLE, null, nestedCreatePlan()));

            assertEquals(20011, exception.getErrorCode());
        }
    }

    // ========== SubPermissionPolicy 判定优先级（§6.5.2 读写同源） ==========

    @Nested
    class SubPermissionPolicyResolution {

        private PermissionGrantPlanDomainService.SubPermissionPolicy resolve(DomainConfig config) {
            return service.parseSubPermissionPolicy(config, "MENU");
        }

        @Test
        void shouldReturnConfigMissingWhenConfigAbsent() {
            var policy = resolve(null);
            assertEquals(PermissionGrantPlanDomainService.SubPermissionPolicy.Mode.ALLOW_NONE, policy.mode());
            assertEquals("CONFIG_MISSING", policy.reason());
            assertFalse(policy.allows("BUTTON"));
        }

        @Test
        void shouldReturnConfigEmptyWhenExtraBlank() {
            DomainConfig config = new DomainConfig();
            config.setExtra("   ");
            var policy = resolve(config);
            assertEquals("CONFIG_EMPTY", policy.reason());
        }

        @Test
        void shouldReturnAllowAllForTopLevelWildcard() {
            DomainConfig config = new DomainConfig();
            config.setExtra(" * ");
            var policy = resolve(config);
            assertEquals(PermissionGrantPlanDomainService.SubPermissionPolicy.Mode.ALLOW_ALL, policy.mode());
            assertTrue(policy.allows("ANY_TYPE"));
        }

        @Test
        void shouldReturnConfigInvalidForNonStringParentTypeScalar() {
            DomainConfig config = new DomainConfig();
            config.setExtra("{\"allowed\":[{\"parent_type\":5,\"child_types\":[\"BUTTON\"]}]}");
            // 数字标量经 asText 会收编为 "5"——契约要求非字符串结构非法，落 CONFIG_INVALID
            assertEquals("CONFIG_INVALID", resolve(config).reason());
        }

        @Test
        void shouldReturnConfigInvalidForNonStringChildTypeScalar() {
            DomainConfig config = new DomainConfig();
            config.setExtra("{\"allowed\":[{\"parent_type\":\"MENU\",\"child_types\":[5]}]}");
            assertEquals("CONFIG_INVALID", resolve(config).reason());
        }

        @Test
        void shouldReturnConfigInvalidForBrokenJson() {
            DomainConfig config = new DomainConfig();
            config.setExtra("{not-json");
            assertEquals("CONFIG_INVALID", resolve(config).reason());
        }

        @Test
        void shouldReturnConfigInvalidForMalformedItemEvenIfUnmatched() {
            DomainConfig config = new DomainConfig();
            config.setExtra("{\"allowed\":[{\"parent_type\":\"API\",\"child_types\":[\"X\"]},{\"child_types\":[]}]}");
            // 非匹配项结构非法同样 CONFIG_INVALID（无法证明属于其他父类型，全局结构错误）
            assertEquals("CONFIG_INVALID", resolve(config).reason());
        }

        @Test
        void shouldReturnParentNotConfiguredWhenNoMatch() {
            DomainConfig config = new DomainConfig();
            config.setExtra("{\"allowed\":[{\"parent_type\":\"API\",\"child_types\":[\"X\"]}]}");
            assertEquals("PARENT_NOT_CONFIGURED", resolve(config).reason());
        }

        @Test
        void shouldReturnAllowAllForNestedWildcard() {
            DomainConfig config = new DomainConfig();
            config.setExtra("{\"allowed\":[{\"parent_type\":\"MENU\",\"child_types\":[\"*\"]}]}");
            var policy = resolve(config);
            assertEquals(PermissionGrantPlanDomainService.SubPermissionPolicy.Mode.ALLOW_ALL, policy.mode());
        }

        @Test
        void shouldReturnUnionDedupedList() {
            DomainConfig config = new DomainConfig();
            config.setExtra("{\"allowed\":[{\"parent_type\":\"MENU\",\"child_types\":[\"BUTTON\"]},"
                + "{\"parent_type\":\"menu\",\"child_types\":[\"DATA\",\"button\"]},{\"parent_type\":\"MENU\",\"child_types\":[]}]}");
            var policy = resolve(config);
            assertEquals(PermissionGrantPlanDomainService.SubPermissionPolicy.Mode.ALLOW_LIST, policy.mode());
            assertEquals(List.of("BUTTON", "DATA"), policy.allowedTypeCodes());
            assertTrue(policy.allows("button"));
            assertFalse(policy.allows("API"));
        }

        @Test
        void shouldReturnChildTypesEmptyWhenAllMatchesEmpty() {
            DomainConfig config = new DomainConfig();
            config.setExtra("{\"allowed\":[{\"parent_type\":\"MENU\",\"child_types\":[]}]}");
            assertEquals("CHILD_TYPES_EMPTY", resolve(config).reason());
        }

        @Test
        void shouldThrow20007ForUnknownParentType() {
            when(typeResolutionService.resolveTypeValue(TENANT, "resource_type", "MENU"))
                .thenReturn(null);

            BizException exception = assertThrows(BizException.class,
                () -> service.resolveSubPermissionPolicy(TENANT, "MENU"));

            assertEquals(20007, exception.getErrorCode());
        }

        @Test
        void shouldResolvePolicyThroughPublicEntryPoint() {
            when(typeResolutionService.resolveTypeValue(TENANT, "resource_type", "MENU"))
                .thenReturn(4);
            DomainConfig config = new DomainConfig();
            config.setBizDomainId(7L);
            config.setConfigType("SUB_PERM");
            config.setExtra("{\"allowed\":[{\"parent_type\":\"MENU\",\"child_types\":[\"BUTTON\"]}]}");
            when(domainClassifyService.findDomainIdsByTypeCodes(TENANT, java.util.Set.of("MENU")))
                .thenReturn(Map.of("MENU", 7L));
            when(domainConfigMapper.selectByTenantId(TENANT)).thenReturn(List.of(config));

            var policy = service.resolveSubPermissionPolicy(TENANT, "MENU");

            assertEquals(PermissionGrantPlanDomainService.SubPermissionPolicy.Mode.ALLOW_LIST, policy.mode());
            assertEquals(List.of("BUTTON"), policy.allowedTypeCodes());
        }
    }
}
