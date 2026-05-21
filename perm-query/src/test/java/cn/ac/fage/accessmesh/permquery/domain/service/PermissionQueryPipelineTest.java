package cn.ac.fage.accessmesh.permquery.domain.service;

import cn.ac.fage.accessmesh.permission.entity.AbstractRole;
import cn.ac.fage.accessmesh.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.permission.entity.PermissionCondition;
import cn.ac.fage.accessmesh.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.permquery.domain.model.valueobject.ConflictInfo;
import cn.ac.fage.accessmesh.permquery.domain.model.valueobject.GrantedPermission;
import cn.ac.fage.accessmesh.permquery.domain.model.valueobject.PermissionQuery;
import cn.ac.fage.accessmesh.permquery.domain.model.valueobject.PermissionQueryResult;
import cn.ac.fage.accessmesh.permquery.domain.model.valueobject.QueryOptions;
import cn.ac.fage.accessmesh.permquery.domain.repository.EntityLoadRepository;
import cn.ac.fage.accessmesh.permquery.domain.repository.PermissionEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 权限查询管线单元测试
 * <p>
 * 测试 PermissionQueryPipeline 核心编排逻辑，覆盖：
 * 1. 正常查询流程（有角色、有权限）
 * 2. 无角色返回空结果
 * 3. 条件评估结果填充
 * 4. 冲突检测结果填充
 * 5. 辅助实体加载（按选项）
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class PermissionQueryPipelineTest {

    /** 角色解析服务 Mock */
    @Mock
    private RoleResolutionService roleResolutionService;

    /** 类型解析服务 Mock */
    @Mock
    private TypeResolver typeResolver;

    /** 位掩码计算服务 Mock */
    @Mock
    private BitMaskCalculator bitMaskCalculator;

    /** 权限条目仓储 Mock */
    @Mock
    private PermissionEntryRepository permissionEntryRepository;

    /** 实体加载仓储 Mock */
    @Mock
    private EntityLoadRepository entityLoadRepository;

    /** 条件评估服务 Mock */
    @Mock
    private ConditionEvaluator conditionEvaluator;

    /** 冲突解决服务 Mock */
    @Mock
    private ConflictResolver conflictResolver;

    /** 待测试的管线实例 */
    private PermissionQueryPipeline pipeline;

    /** 测试租户ID */
    private static final Long TENANT_ID = 1L;

    /** 测试用户ID */
    private static final Long USER_ID = 100L;

    /** 测试角色ID */
    private static final Long ROLE_ID = 10L;

    /** 测试资源类型值 */
    private static final Integer RESOURCE_TYPE = 1;

    /** 测试资源实体ID */
    private static final Long RESOURCE_ENTITY_ID = 1000L;

    /** 测试操作ID */
    private static final Long OPERATION_ID = 100L;

    /** 测试权限ID */
    private static final Long PERMISSION_ID = 500L;

    /** 测试条件ID */
    private static final Long CONDITION_ID = 200L;

    @BeforeEach
    void setUp() {
        pipeline = new PermissionQueryPipeline(
            roleResolutionService,
            typeResolver,
            bitMaskCalculator,
            permissionEntryRepository,
            entityLoadRepository,
            conditionEvaluator,
            conflictResolver
        );
    }

    // ========== 正常查询流程测试 ==========

    @Nested
    @DisplayName("正常查询流程测试")
    class NormalQueryTests {

        @Test
        @DisplayName("完整查询流程 - 有角色、有类型级权限")
        void shouldExecuteFullPipelineWithScopeAllPermissions() {
            // Given: 构建完整查询参数
            PermissionQuery query = PermissionQuery.forSingle(
                TENANT_ID, USER_ID, "API", "user:list", "VIEW", QueryOptions.full()
            );

            // Mock 角色解析
            Set<Long> roleIds = Set.of(ROLE_ID);
            when(roleResolutionService.resolve(TENANT_ID, USER_ID)).thenReturn(roleIds);

            // Mock 类型解析
            Set<Integer> resourceTypes = Set.of(RESOURCE_TYPE);
            when(typeResolver.resolveResourceTypes(query)).thenReturn(resourceTypes);

            // Mock 操作解析
            Set<Long> operationIds = Set.of(OPERATION_ID);
            when(typeResolver.resolveOperations(query)).thenReturn(operationIds);

            // Mock 位掩码计算
            Map<Integer, Long> bitMasks = Map.of(RESOURCE_TYPE, 1L);
            when(bitMaskCalculator.calculate(TENANT_ID, resourceTypes, operationIds)).thenReturn(bitMasks);

            // Mock 类型级权限查询
            GrantedPermission scopeAllPerm = createGrantedPermission(PERMISSION_ID, ROLE_ID, RESOURCE_TYPE, null, 1L);
            when(permissionEntryRepository.queryScopeAllPermissions(TENANT_ID, roleIds, bitMasks))
                .thenReturn(List.of(scopeAllPerm));

            // Mock 实例级权限查询 - 无实例权限
            when(permissionEntryRepository.queryInstancePermissions(anyLong(), anySet(), anySet(), any()))
                .thenReturn(List.of());

            // Mock 条件评估
            Map<Long, Boolean> conditionResults = Map.of(PERMISSION_ID, true);
            when(conditionEvaluator.evaluate(anyLong(), any(), any()))
                .thenReturn(conditionResults);
            when(conditionEvaluator.applyEvaluationResults(any(), any()))
                .thenReturn(List.of(scopeAllPerm.withConditionMet(true)));

            // Mock 冲突检测
            when(conflictResolver.detectConflicts(anyLong(), any())).thenReturn(List.of());
            when(conflictResolver.applyConflictMarks(any(), any())).thenReturn(List.of(scopeAllPerm.withConditionMet(true)));

            // Mock 辅助实体加载
            AbstractRole role = createRole(ROLE_ID, "Admin");
            when(entityLoadRepository.loadRoles(TENANT_ID, roleIds)).thenReturn(Map.of(ROLE_ID, role));

            OperationPermission op = createOperationPermission(OPERATION_ID, RESOURCE_TYPE, "VIEW", 1L);
            when(entityLoadRepository.loadOperationsByResourceTypes(TENANT_ID, Set.of(RESOURCE_TYPE)))
                .thenReturn(Map.of(RESOURCE_TYPE, List.of(op)));

            // When: 执行管线
            PermissionQueryResult result = pipeline.execute(query);

            // Then: 验证结果
            assertNotNull(result);
            assertEquals(TENANT_ID, result.tenantId());
            assertEquals(roleIds, result.resolvedRoleIds());
            assertEquals(resourceTypes, result.resolvedResourceTypes());

            // 验证有类型级权限
            assertTrue(result.hasScopeAllPermission());
            assertFalse(result.hasInstancePermission());
            assertTrue(result.hasAnyPermission());

            // 验证管线步骤被调用
            verify(roleResolutionService).resolve(TENANT_ID, USER_ID);
            verify(typeResolver).resolveResourceTypes(query);
            verify(typeResolver).resolveOperations(query);
            verify(bitMaskCalculator).calculate(TENANT_ID, resourceTypes, operationIds);
            verify(permissionEntryRepository).queryScopeAllPermissions(TENANT_ID, roleIds, bitMasks);
            verify(conditionEvaluator).evaluate(TENANT_ID, List.of(scopeAllPerm), Map.of());
            verify(conflictResolver).detectConflicts(TENANT_ID, List.of(scopeAllPerm));
            verify(entityLoadRepository).loadRoles(TENANT_ID, roleIds);
        }

        @Test
        @DisplayName("完整查询流程 - 有角色、有实例级权限")
        void shouldExecuteFullPipelineWithInstancePermissions() {
            // Given: 构建查询参数（带资源实体ID）
            PermissionQuery query = PermissionQuery.withEntityIds(
                TENANT_ID, USER_ID, null,
                Set.of(RESOURCE_ENTITY_ID),
                Set.of("VIEW"),
                QueryOptions.full()
            );

            // Mock 角色解析
            Set<Long> roleIds = Set.of(ROLE_ID);
            when(roleResolutionService.resolve(TENANT_ID, USER_ID)).thenReturn(roleIds);

            // Mock 类型解析
            Set<Integer> resourceTypes = Set.of(RESOURCE_TYPE);
            when(typeResolver.resolveResourceTypes(query)).thenReturn(resourceTypes);

            // Mock 操作解析
            Set<Long> operationIds = Set.of(OPERATION_ID);
            when(typeResolver.resolveOperations(query)).thenReturn(operationIds);

            // Mock 位掩码计算
            Map<Integer, Long> bitMasks = Map.of(RESOURCE_TYPE, 1L);
            when(bitMaskCalculator.calculate(TENANT_ID, resourceTypes, operationIds)).thenReturn(bitMasks);

            // Mock 类型级权限查询
            when(permissionEntryRepository.queryScopeAllPermissions(anyLong(), anySet(), any()))
                .thenReturn(List.of());

            // Mock 实例级权限查询
            GrantedPermission instancePerm = createGrantedPermission(
                PERMISSION_ID, ROLE_ID, RESOURCE_TYPE, RESOURCE_ENTITY_ID, 1L
            );
            when(permissionEntryRepository.queryInstancePermissions(TENANT_ID, roleIds, Set.of(RESOURCE_ENTITY_ID), bitMasks))
                .thenReturn(List.of(instancePerm));

            // Mock 条件评估和冲突检测
            when(conditionEvaluator.evaluate(anyLong(), any(), any())).thenReturn(Map.of());
            when(conditionEvaluator.applyEvaluationResults(any(), any())).thenReturn(List.of(instancePerm));
            when(conflictResolver.detectConflicts(anyLong(), any())).thenReturn(List.of());
            when(conflictResolver.applyConflictMarks(any(), any())).thenReturn(List.of(instancePerm));

            // Mock 资源实体加载
            ResourceEntity resource = createResourceEntity(RESOURCE_ENTITY_ID, "user:list");
            when(entityLoadRepository.loadResources(TENANT_ID, Set.of(RESOURCE_ENTITY_ID)))
                .thenReturn(Map.of(RESOURCE_ENTITY_ID, resource));

            // When: 执行管线
            PermissionQueryResult result = pipeline.execute(query);

            // Then: 验证结果
            assertNotNull(result);
            assertTrue(result.hasInstancePermission());
            assertFalse(result.hasScopeAllPermission());
            assertTrue(result.hasAnyPermission());

            assertEquals(1, result.instancePermissions().size());
            assertEquals(RESOURCE_ENTITY_ID, result.instancePermissions().get(0).resourceEntityId());
        }

        @Test
        @DisplayName("完整查询流程 - 同时有类型级和实例级权限")
        void shouldExecuteFullPipelineWithBothPermissionTypes() {
            // Given: 构建查询参数
            PermissionQuery query = PermissionQuery.forSingle(
                TENANT_ID, USER_ID, "API", "user:list", "VIEW", QueryOptions.full()
            );

            // Mock 角色解析
            Set<Long> roleIds = Set.of(ROLE_ID);
            when(roleResolutionService.resolve(TENANT_ID, USER_ID)).thenReturn(roleIds);

            // Mock 类型解析
            Set<Integer> resourceTypes = Set.of(RESOURCE_TYPE);
            when(typeResolver.resolveResourceTypes(query)).thenReturn(resourceTypes);
            when(typeResolver.resolveOperations(query)).thenReturn(Set.of(OPERATION_ID));

            // Mock 位掩码计算
            Map<Integer, Long> bitMasks = Map.of(RESOURCE_TYPE, 1L);
            when(bitMaskCalculator.calculate(TENANT_ID, resourceTypes, Set.of(OPERATION_ID))).thenReturn(bitMasks);

            // Mock 两种权限查询
            GrantedPermission scopeAllPerm = createGrantedPermission(PERMISSION_ID, ROLE_ID, RESOURCE_TYPE, null, 1L);
            GrantedPermission instancePerm = createGrantedPermission(PERMISSION_ID + 1, ROLE_ID, RESOURCE_TYPE, RESOURCE_ENTITY_ID, 2L);

            when(permissionEntryRepository.queryScopeAllPermissions(anyLong(), anySet(), any()))
                .thenReturn(List.of(scopeAllPerm));
            when(typeResolver.resolveResources(query)).thenReturn(Set.of(RESOURCE_ENTITY_ID));
            when(permissionEntryRepository.queryInstancePermissions(anyLong(), anySet(), anySet(), any()))
                .thenReturn(List.of(instancePerm));

            // Mock 条件评估和冲突检测
            when(conditionEvaluator.evaluate(anyLong(), any(), any())).thenReturn(Map.of());
            when(conditionEvaluator.applyEvaluationResults(any(), any()))
                .thenReturn(List.of(scopeAllPerm, instancePerm));
            when(conflictResolver.detectConflicts(anyLong(), any())).thenReturn(List.of());
            when(conflictResolver.applyConflictMarks(any(), any()))
                .thenReturn(List.of(scopeAllPerm, instancePerm));

            // When: 执行管线
            PermissionQueryResult result = pipeline.execute(query);

            // Then: 验证同时有两种权限
            assertTrue(result.hasScopeAllPermission());
            assertTrue(result.hasInstancePermission());
            assertEquals(2, result.allPermissions().size());
        }
    }

    // ========== 无角色返回空结果测试 ==========

    @Nested
    @DisplayName("无角色返回空结果测试")
    class NoRoleTests {

        @Test
        @DisplayName("用户无角色 - 返回空结果")
        void shouldReturnEmptyResultWhenUserHasNoRoles() {
            // Given: 用户无角色
            PermissionQuery query = PermissionQuery.forSingle(
                TENANT_ID, USER_ID, "API", "user:list", "VIEW", QueryOptions.full()
            );

            when(roleResolutionService.resolve(TENANT_ID, USER_ID)).thenReturn(Collections.emptySet());

            // When: 执行管线
            PermissionQueryResult result = pipeline.execute(query);

            // Then: 返回空结果
            assertNotNull(result);
            assertTrue(result.resolvedRoleIds().isEmpty());
            assertFalse(result.hasAnyPermission());
            assertFalse(result.hasScopeAllPermission());
            assertFalse(result.hasInstancePermission());

            // 验证后续步骤未执行
            verify(typeResolver, never()).resolveResourceTypes(any());
            verify(bitMaskCalculator, never()).calculate(anyLong(), anySet(), anySet());
            verify(permissionEntryRepository, never()).queryScopeAllPermissions(anyLong(), anySet(), any());
        }

        @Test
        @DisplayName("userId为null且roleIds为空 - 返回空结果")
        void shouldReturnEmptyResultWhenUserIdIsNullAndNoRoleIds() {
            // Given: userId为null，roleIds也为空
            PermissionQuery query = PermissionQuery.withRoles(
                TENANT_ID, Collections.emptySet(), Set.of("API"), Set.of("VIEW"), QueryOptions.minimal()
            );

            // When: 执行管线
            PermissionQueryResult result = pipeline.execute(query);

            // Then: 返回空结果
            assertNotNull(result);
            assertTrue(result.resolvedRoleIds().isEmpty());
            assertFalse(result.hasAnyPermission());

            // 角色解析服务未被调用（因为roleIds已指定为空）
            verify(roleResolutionService, never()).resolve(anyLong(), anyLong());
        }

        @Test
        @DisplayName("查询参数为null - 返回空结果")
        void shouldReturnEmptyResultWhenQueryIsNull() {
            // When: 传入null
            PermissionQueryResult result = pipeline.execute(null);

            // Then: 返回空结果
            assertNotNull(result);
            assertFalse(result.hasAnyPermission());
            assertEquals(PermissionQueryResult.empty(), result);
        }
    }

    // ========== 条件评估结果填充测试 ==========

    @Nested
    @DisplayName("条件评估结果填充测试")
    class ConditionEvaluationTests {

        @Test
        @DisplayName("条件评估 - 条件满足")
        void shouldFillConditionMetWhenConditionSatisfied() {
            // Given: 构建带条件的权限查询
            GrantedPermission permWithCondition = createGrantedPermissionWithCondition(
                PERMISSION_ID, ROLE_ID, RESOURCE_TYPE, null, 1L, CONDITION_ID
            );

            PermissionQuery query = PermissionQuery.forSingle(
                TENANT_ID, USER_ID, "API", "user:list", "VIEW",
                QueryOptions.full().withContext(Map.of("currentDate", "2025-01-15"))
            );

            setupBasicMocks(query);

            when(permissionEntryRepository.queryScopeAllPermissions(anyLong(), anySet(), any()))
                .thenReturn(List.of(permWithCondition));

            // Mock 条件评估 - 条件满足
            Map<Long, Boolean> conditionResults = Map.of(PERMISSION_ID, true);
            when(conditionEvaluator.evaluate(TENANT_ID, List.of(permWithCondition), Map.of("currentDate", "2025-01-15")))
                .thenReturn(conditionResults);

            GrantedPermission permWithResult = permWithCondition.withConditionMet(true);
            when(conditionEvaluator.applyEvaluationResults(List.of(permWithCondition), conditionResults))
                .thenReturn(List.of(permWithResult));

            // Mock 冲突检测
            when(conflictResolver.detectConflicts(anyLong(), any())).thenReturn(List.of());
            when(conflictResolver.applyConflictMarks(any(), any())).thenReturn(List.of(permWithResult));

            // When: 执行管线
            PermissionQueryResult result = pipeline.execute(query);

            // Then: 验证条件评估结果
            assertNotNull(result.conditionResults());
            assertTrue(result.conditionResults().get(PERMISSION_ID));

            // 验证权限条目中条件满足
            GrantedPermission resultPerm = result.scopeAllPermissions().get(0);
            assertTrue(resultPerm.isConditionSatisfied());
            assertFalse(result.hasUnmetConditions());
        }

        @Test
        @DisplayName("条件评估 - 条件不满足")
        void shouldFillConditionNotMetWhenConditionUnsatisfied() {
            // Given: 构建带条件的权限查询
            GrantedPermission permWithCondition = createGrantedPermissionWithCondition(
                PERMISSION_ID, ROLE_ID, RESOURCE_TYPE, null, 1L, CONDITION_ID
            );

            PermissionQuery query = PermissionQuery.forSingle(
                TENANT_ID, USER_ID, "API", "user:list", "VIEW",
                QueryOptions.full().withContext(Map.of("currentDate", "2025-01-15"))
            );

            setupBasicMocks(query);

            when(permissionEntryRepository.queryScopeAllPermissions(anyLong(), anySet(), any()))
                .thenReturn(List.of(permWithCondition));

            // Mock 条件评估 - 条件不满足
            Map<Long, Boolean> conditionResults = Map.of(PERMISSION_ID, false);
            when(conditionEvaluator.evaluate(TENANT_ID, List.of(permWithCondition), Map.of("currentDate", "2025-01-15")))
                .thenReturn(conditionResults);

            GrantedPermission permWithResult = permWithCondition.withConditionMet(false);
            when(conditionEvaluator.applyEvaluationResults(List.of(permWithCondition), conditionResults))
                .thenReturn(List.of(permWithResult));

            // Mock 冲突检测
            when(conflictResolver.detectConflicts(anyLong(), any())).thenReturn(List.of());
            when(conflictResolver.applyConflictMarks(any(), any())).thenReturn(List.of(permWithResult));

            // When: 执行管线
            PermissionQueryResult result = pipeline.execute(query);

            // Then: 验证条件评估结果
            assertNotNull(result.conditionResults());
            assertFalse(result.conditionResults().get(PERMISSION_ID));

            // 验证权限条目中条件不满足
            GrantedPermission resultPerm = result.scopeAllPermissions().get(0);
            assertFalse(resultPerm.isConditionSatisfied());
            assertTrue(result.hasUnmetConditions());
        }

        @Test
        @DisplayName("不评估条件 - 跳过评估步骤")
        void shouldSkipConditionEvaluationWhenOptionDisabled() {
            // Given: 使用不评估条件的选项
            PermissionQuery query = PermissionQuery.forSingle(
                TENANT_ID, USER_ID, "API", "user:list", "VIEW", QueryOptions.minimal()
            );

            GrantedPermission perm = createGrantedPermission(PERMISSION_ID, ROLE_ID, RESOURCE_TYPE, null, 1L);

            setupBasicMocks(query);
            when(permissionEntryRepository.queryScopeAllPermissions(anyLong(), anySet(), any()))
                .thenReturn(List.of(perm));

            // When: 执行管线
            PermissionQueryResult result = pipeline.execute(query);

            // Then: 条件评估未被调用
            verify(conditionEvaluator, never()).evaluate(anyLong(), any(), any());
            verify(conditionEvaluator, never()).applyEvaluationResults(any(), any());

            // 结果中无条件评估数据
            assertTrue(result.conditionResults().isEmpty());
        }
    }

    // ========== 冲突检测结果填充测试 ==========

    @Nested
    @DisplayName("冲突检测结果填充测试")
    class ConflictDetectionTests {

        @Test
        @DisplayName("冲突检测 - 发现角色互斥冲突")
        void shouldFillConflictInfoWhenRoleMutexDetected() {
            // Given: 两个权限来自互斥角色
            Long roleId1 = 10L;
            Long roleId2 = 20L;
            Long permId1 = 500L;
            Long permId2 = 600L;

            GrantedPermission perm1 = createGrantedPermission(permId1, roleId1, RESOURCE_TYPE, null, 1L);
            GrantedPermission perm2 = createGrantedPermission(permId2, roleId2, RESOURCE_TYPE, null, 2L);

            PermissionQuery query = PermissionQuery.forValidate(TENANT_ID, USER_ID, "API", "user:list", "VIEW");
            setupBasicMocks(query);

            when(permissionEntryRepository.queryScopeAllPermissions(anyLong(), anySet(), any()))
                .thenReturn(List.of(perm1, perm2));

            // Mock 冲突检测 - 发现角色互斥
            ConflictInfo conflict = ConflictInfo.roleMutex(roleId1, roleId2, permId1, permId2);
            when(conflictResolver.detectConflicts(TENANT_ID, List.of(perm1, perm2)))
                .thenReturn(List.of(conflict));

            GrantedPermission perm1WithConflict = perm1.withConflict(true);
            GrantedPermission perm2WithConflict = perm2.withConflict(true);
            when(conflictResolver.applyConflictMarks(List.of(perm1, perm2), List.of(conflict)))
                .thenReturn(List.of(perm1WithConflict, perm2WithConflict));

            // Mock 条件评估
            when(conditionEvaluator.evaluate(anyLong(), any(), any())).thenReturn(Map.of());
            when(conditionEvaluator.applyEvaluationResults(any(), any()))
                .thenReturn(List.of(perm1WithConflict, perm2WithConflict));

            // When: 执行管线
            PermissionQueryResult result = pipeline.execute(query);

            // Then: 验证冲突信息
            assertTrue(result.hasConflicts());
            assertEquals(1, result.conflicts().size());

            ConflictInfo resultConflict = result.conflicts().get(0);
            assertTrue(resultConflict.isRoleMutex());
            assertEquals(roleId1, resultConflict.firstRoleId());
            assertEquals(roleId2, resultConflict.secondRoleId());

            // 验证权限条目被标记为冲突
            assertTrue(result.allPermissions().stream().anyMatch(GrantedPermission::isInConflict));
        }

        @Test
        @DisplayName("冲突检测 - 发现权限互斥冲突")
        void shouldFillConflictInfoWhenPermMutexDetected() {
            // Given: 两个权限操作互斥
            Long permId1 = 500L;
            Long permId2 = 600L;

            GrantedPermission perm1 = createGrantedPermission(permId1, ROLE_ID, RESOURCE_TYPE, null, 1L);
            GrantedPermission perm2 = createGrantedPermission(permId2, ROLE_ID, RESOURCE_TYPE, null, 2L);

            PermissionQuery query = PermissionQuery.forValidate(TENANT_ID, USER_ID, "API", "user:list", "VIEW");
            setupBasicMocks(query);

            when(permissionEntryRepository.queryScopeAllPermissions(anyLong(), anySet(), any()))
                .thenReturn(List.of(perm1, perm2));

            // Mock 冲突检测 - 发现权限互斥
            ConflictInfo conflict = ConflictInfo.permMutex(permId1, permId2);
            when(conflictResolver.detectConflicts(TENANT_ID, List.of(perm1, perm2)))
                .thenReturn(List.of(conflict));

            GrantedPermission perm1WithConflict = perm1.withConflict(true);
            GrantedPermission perm2WithConflict = perm2.withConflict(true);
            when(conflictResolver.applyConflictMarks(List.of(perm1, perm2), List.of(conflict)))
                .thenReturn(List.of(perm1WithConflict, perm2WithConflict));

            // Mock 条件评估
            when(conditionEvaluator.evaluate(anyLong(), any(), any())).thenReturn(Map.of());
            when(conditionEvaluator.applyEvaluationResults(any(), any()))
                .thenReturn(List.of(perm1WithConflict, perm2WithConflict));

            // When: 执行管线
            PermissionQueryResult result = pipeline.execute(query);

            // Then: 验证冲突信息
            assertTrue(result.hasConflicts());
            ConflictInfo resultConflict = result.conflicts().get(0);
            assertTrue(resultConflict.isPermMutex());
            assertEquals(permId1, resultConflict.firstPermissionId());
            assertEquals(permId2, resultConflict.secondPermissionId());
        }

        @Test
        @DisplayName("不检测冲突 - 跳过冲突检测步骤")
        void shouldSkipConflictDetectionWhenOptionDisabled() {
            // Given: 使用不检测冲突的选项
            PermissionQuery query = PermissionQuery.forSingle(
                TENANT_ID, USER_ID, "API", "user:list", "VIEW", QueryOptions.minimal()
            );

            GrantedPermission perm = createGrantedPermission(PERMISSION_ID, ROLE_ID, RESOURCE_TYPE, null, 1L);

            setupBasicMocks(query);
            when(permissionEntryRepository.queryScopeAllPermissions(anyLong(), anySet(), any()))
                .thenReturn(List.of(perm));

            // When: 执行管线
            PermissionQueryResult result = pipeline.execute(query);

            // Then: 冲突检测未被调用
            verify(conflictResolver, never()).detectConflicts(anyLong(), any());
            verify(conflictResolver, never()).applyConflictMarks(any(), any());

            // 结果中无冲突信息
            assertFalse(result.hasConflicts());
        }
    }

    // ========== 辅助实体加载测试 ==========

    @Nested
    @DisplayName("辅助实体加载测试")
    class AncillaryEntityLoadTests {

        @Test
        @DisplayName("加载角色实体 - 按选项")
        void shouldLoadRolesWhenOptionEnabled() {
            // Given: 启用角色加载
            PermissionQuery query = PermissionQuery.forSingle(
                TENANT_ID, USER_ID, "API", "user:list", "VIEW", QueryOptions.forView()
            );

            setupBasicMocks(query);
            when(permissionEntryRepository.queryScopeAllPermissions(anyLong(), anySet(), any()))
                .thenReturn(List.of());

            // Mock 角色加载
            AbstractRole role = createRole(ROLE_ID, "Admin");
            when(entityLoadRepository.loadRoles(TENANT_ID, Set.of(ROLE_ID)))
                .thenReturn(Map.of(ROLE_ID, role));

            // When: 执行管线
            PermissionQueryResult result = pipeline.execute(query);

            // Then: 验证角色加载
            verify(entityLoadRepository).loadRoles(TENANT_ID, Set.of(ROLE_ID));
            assertNotNull(result.roles());
            assertEquals(1, result.roles().size());
            assertEquals("Admin", result.roles().get(ROLE_ID).getName());
        }

        @Test
        @DisplayName("加载资源实体 - 按选项")
        void shouldLoadResourcesWhenOptionEnabled() {
            // Given: 启用资源加载，有实例级权限
            GrantedPermission instancePerm = createGrantedPermission(
                PERMISSION_ID, ROLE_ID, RESOURCE_TYPE, RESOURCE_ENTITY_ID, 1L
            );

            PermissionQuery query = PermissionQuery.withEntityIds(
                TENANT_ID, USER_ID, null,
                Set.of(RESOURCE_ENTITY_ID),
                Set.of("VIEW"),
                QueryOptions.forView()
            );

            setupBasicMocks(query);
            when(permissionEntryRepository.queryScopeAllPermissions(anyLong(), anySet(), any()))
                .thenReturn(List.of());
            when(permissionEntryRepository.queryInstancePermissions(anyLong(), anySet(), anySet(), any()))
                .thenReturn(List.of(instancePerm));

            // Mock 条件评估和冲突检测
            when(conditionEvaluator.evaluate(anyLong(), any(), any())).thenReturn(Map.of());
            when(conflictResolver.detectConflicts(anyLong(), any())).thenReturn(List.of());

            // Mock 资源加载
            ResourceEntity resource = createResourceEntity(RESOURCE_ENTITY_ID, "user:list");
            when(entityLoadRepository.loadResources(TENANT_ID, Set.of(RESOURCE_ENTITY_ID)))
                .thenReturn(Map.of(RESOURCE_ENTITY_ID, resource));

            // When: 执行管线
            PermissionQueryResult result = pipeline.execute(query);

            // Then: 验证资源加载
            verify(entityLoadRepository).loadResources(TENANT_ID, Set.of(RESOURCE_ENTITY_ID));
            assertNotNull(result.resources());
            assertEquals(1, result.resources().size());
            assertEquals("user:list", result.resources().get(RESOURCE_ENTITY_ID).getCode());
        }

        @Test
        @DisplayName("加载操作权限实体 - 按选项")
        void shouldLoadOperationsWhenOptionEnabled() {
            // Given: 启用操作加载
            GrantedPermission perm = createGrantedPermission(PERMISSION_ID, ROLE_ID, RESOURCE_TYPE, null, 1L);

            PermissionQuery query = PermissionQuery.forSingle(
                TENANT_ID, USER_ID, "API", "user:list", "VIEW", QueryOptions.forView()
            );

            setupBasicMocks(query);
            when(permissionEntryRepository.queryScopeAllPermissions(anyLong(), anySet(), any()))
                .thenReturn(List.of(perm));

            // Mock 条件评估和冲突检测
            when(conditionEvaluator.evaluate(anyLong(), any(), any())).thenReturn(Map.of());
            when(conflictResolver.detectConflicts(anyLong(), any())).thenReturn(List.of());

            // Mock 操作加载
            OperationPermission op = createOperationPermission(OPERATION_ID, RESOURCE_TYPE, "VIEW", 1L);
            when(entityLoadRepository.loadOperationsByResourceTypes(TENANT_ID, Set.of(RESOURCE_TYPE)))
                .thenReturn(Map.of(RESOURCE_TYPE, List.of(op)));

            // When: 执行管线
            PermissionQueryResult result = pipeline.execute(query);

            // Then: 验证操作加载
            verify(entityLoadRepository).loadOperationsByResourceTypes(TENANT_ID, Set.of(RESOURCE_TYPE));
            assertNotNull(result.operations());
        }

        @Test
        @DisplayName("加载条件实体 - 按选项")
        void shouldLoadConditionsWhenOptionEnabled() {
            // Given: 启用条件加载，有带条件的权限
            GrantedPermission permWithCondition = createGrantedPermissionWithCondition(
                PERMISSION_ID, ROLE_ID, RESOURCE_TYPE, null, 1L, CONDITION_ID
            );

            PermissionQuery query = PermissionQuery.forSingle(
                TENANT_ID, USER_ID, "API", "user:list", "VIEW", QueryOptions.forView()
            );

            setupBasicMocks(query);
            when(permissionEntryRepository.queryScopeAllPermissions(anyLong(), anySet(), any()))
                .thenReturn(List.of(permWithCondition));

            // Mock 条件评估和冲突检测
            when(conditionEvaluator.evaluate(anyLong(), any(), any())).thenReturn(Map.of());
            when(conflictResolver.detectConflicts(anyLong(), any())).thenReturn(List.of());

            // Mock 条件加载
            PermissionCondition condition = createCondition(CONDITION_ID, "date-range");
            when(entityLoadRepository.loadConditions(TENANT_ID, Set.of(CONDITION_ID)))
                .thenReturn(Map.of(CONDITION_ID, condition));

            // When: 执行管线
            PermissionQueryResult result = pipeline.execute(query);

            // Then: 验证条件加载
            verify(entityLoadRepository).loadConditions(TENANT_ID, Set.of(CONDITION_ID));
            assertNotNull(result.conditions());
            assertEquals(1, result.conditions().size());
            assertEquals("date-range", result.conditions().get(CONDITION_ID).getCode());
        }

        @Test
        @DisplayName("不加载任何辅助实体 - 最小选项")
        void shouldNotLoadAnyEntitiesWhenMinimalOption() {
            // Given: 使用最小选项
            PermissionQuery query = PermissionQuery.forSingle(
                TENANT_ID, USER_ID, "API", "user:list", "VIEW", QueryOptions.minimal()
            );

            GrantedPermission perm = createGrantedPermission(PERMISSION_ID, ROLE_ID, RESOURCE_TYPE, null, 1L);

            setupBasicMocks(query);
            when(permissionEntryRepository.queryScopeAllPermissions(anyLong(), anySet(), any()))
                .thenReturn(List.of(perm));

            // When: 执行管线
            PermissionQueryResult result = pipeline.execute(query);

            // Then: 所有实体加载未被调用
            verify(entityLoadRepository, never()).loadRoles(anyLong(), anySet());
            verify(entityLoadRepository, never()).loadResources(anyLong(), anySet());
            verify(entityLoadRepository, never()).loadOperationsByResourceTypes(anyLong(), anySet());
            verify(entityLoadRepository, never()).loadConditions(anyLong(), anySet());

            // 结果中无实体数据
            assertTrue(result.roles().isEmpty());
            assertTrue(result.resources().isEmpty());
            assertTrue(result.operations().isEmpty());
            assertTrue(result.conditions().isEmpty());
        }
    }

    // ========== 边界情况测试 ==========

    @Nested
    @DisplayName("边界情况测试")
    class EdgeCaseTests {

        @Test
        @DisplayName("位掩码为空 - 跳过权限查询")
        void shouldSkipPermissionQueryWhenBitMaskEmpty() {
            // Given: 位掩码计算返回空
            PermissionQuery query = PermissionQuery.forSingle(
                TENANT_ID, USER_ID, "API", "user:list", "VIEW", QueryOptions.minimal()
            );

            Set<Long> roleIds = Set.of(ROLE_ID);
            when(roleResolutionService.resolve(TENANT_ID, USER_ID)).thenReturn(roleIds);
            when(typeResolver.resolveResourceTypes(query)).thenReturn(Set.of(RESOURCE_TYPE));
            when(typeResolver.resolveOperations(query)).thenReturn(Set.of(OPERATION_ID));
            when(bitMaskCalculator.calculate(TENANT_ID, Set.of(RESOURCE_TYPE), Set.of(OPERATION_ID)))
                .thenReturn(Collections.emptyMap());

            // When: 执行管线
            PermissionQueryResult result = pipeline.execute(query);

            // Then: 权限查询未被调用
            verify(permissionEntryRepository, never()).queryScopeAllPermissions(anyLong(), anySet(), any());
            verify(permissionEntryRepository, never()).queryInstancePermissions(anyLong(), anySet(), anySet(), any());

            // 结果无权限
            assertFalse(result.hasAnyPermission());
        }

        @Test
        @DisplayName("直接使用roleIds - 跳过角色解析")
        void shouldUseProvidedRoleIdsDirectly() {
            // Given: 查询参数直接指定roleIds
            Set<Long> providedRoleIds = Set.of(10L, 20L);
            PermissionQuery query = PermissionQuery.withRoles(
                TENANT_ID, providedRoleIds, Set.of("API"), Set.of("VIEW"), QueryOptions.minimal()
            );

            when(typeResolver.resolveResourceTypes(query)).thenReturn(Set.of(RESOURCE_TYPE));
            when(typeResolver.resolveOperations(query)).thenReturn(Set.of(OPERATION_ID));
            when(bitMaskCalculator.calculate(TENANT_ID, Set.of(RESOURCE_TYPE), Set.of(OPERATION_ID)))
                .thenReturn(Map.of(RESOURCE_TYPE, 1L));

            GrantedPermission perm = createGrantedPermission(PERMISSION_ID, 10L, RESOURCE_TYPE, null, 1L);
            when(permissionEntryRepository.queryScopeAllPermissions(TENANT_ID, providedRoleIds, Map.of(RESOURCE_TYPE, 1L)))
                .thenReturn(List.of(perm));

            // When: 执行管线
            PermissionQueryResult result = pipeline.execute(query);

            // Then: 角色解析服务未被调用
            verify(roleResolutionService, never()).resolve(anyLong(), anyLong());

            // 直接使用提供的roleIds
            assertEquals(providedRoleIds, result.resolvedRoleIds());
        }

        @Test
        @DisplayName("无实例级权限查询选项 - 跳过实例查询")
        void shouldSkipInstanceQueryWhenOptionDisabled() {
            // Given: 仅查询类型级权限
            PermissionQuery query = PermissionQuery.withRoles(
                TENANT_ID, Set.of(ROLE_ID), Set.of("API"), Set.of("VIEW"),
                new QueryOptions(true, false, false, false, false, false, false, false, Map.of())
            );

            when(typeResolver.resolveResourceTypes(query)).thenReturn(Set.of(RESOURCE_TYPE));
            when(typeResolver.resolveOperations(query)).thenReturn(Set.of(OPERATION_ID));
            when(bitMaskCalculator.calculate(TENANT_ID, Set.of(RESOURCE_TYPE), Set.of(OPERATION_ID)))
                .thenReturn(Map.of(RESOURCE_TYPE, 1L));

            GrantedPermission scopeAllPerm = createGrantedPermission(PERMISSION_ID, ROLE_ID, RESOURCE_TYPE, null, 1L);
            when(permissionEntryRepository.queryScopeAllPermissions(anyLong(), anySet(), any()))
                .thenReturn(List.of(scopeAllPerm));

            // When: 执行管线
            PermissionQueryResult result = pipeline.execute(query);

            // Then: 实例级权限查询未被调用
            verify(permissionEntryRepository, never()).queryInstancePermissions(anyLong(), anySet(), anySet(), any());

            // 仅类型级权限
            assertTrue(result.hasScopeAllPermission());
            assertFalse(result.hasInstancePermission());
        }

        @Test
        @DisplayName("资源实体ID集合为空 - 跳过实例查询")
        void shouldSkipInstanceQueryWhenNoEntityIds() {
            // Given: 无资源实体ID
            PermissionQuery query = PermissionQuery.forSingle(
                TENANT_ID, USER_ID, "API", "user:list", "VIEW", QueryOptions.full()
            );

            setupBasicMocks(query);
            when(permissionEntryRepository.queryScopeAllPermissions(anyLong(), anySet(), any()))
                .thenReturn(List.of());

            // Mock 资源解析返回空
            when(typeResolver.resolveResources(query)).thenReturn(Collections.emptySet());

            // When: 执行管线
            PermissionQueryResult result = pipeline.execute(query);

            // Then: 实例级权限查询未被调用（因为无实体ID）
            verify(permissionEntryRepository, never()).queryInstancePermissions(anyLong(), anySet(), anySet(), any());
        }
    }

    // ========== 辅助方法 ==========

    private void setupBasicMocks(PermissionQuery query) {
        Set<Long> roleIds = Set.of(ROLE_ID);
        when(roleResolutionService.resolve(TENANT_ID, USER_ID)).thenReturn(roleIds);
        when(typeResolver.resolveResourceTypes(query)).thenReturn(Set.of(RESOURCE_TYPE));
        when(typeResolver.resolveOperations(query)).thenReturn(Set.of(OPERATION_ID));
        when(bitMaskCalculator.calculate(TENANT_ID, Set.of(RESOURCE_TYPE), Set.of(OPERATION_ID)))
            .thenReturn(Map.of(RESOURCE_TYPE, 1L));
    }

    private GrantedPermission createGrantedPermission(Long permId, Long roleId, Integer resourceType,
                                                       Long resourceEntityId, long grantedBits) {
        return new GrantedPermission(
            permId, roleId, resourceType, resourceEntityId,
            grantedBits, "VIEW", grantedBits, "DIRECT",
            false, null, null, null, false
        );
    }

    private GrantedPermission createGrantedPermissionWithCondition(Long permId, Long roleId, Integer resourceType,
                                                                    Long resourceEntityId, long grantedBits, Long conditionId) {
        return new GrantedPermission(
            permId, roleId, resourceType, resourceEntityId,
            grantedBits, "VIEW", grantedBits, "DIRECT",
            false, conditionId, null, null, false
        );
    }

    private AbstractRole createRole(Long id, String name) {
        AbstractRole role = new AbstractRole();
        role.setId(id);
        role.setName(name);
        role.setTenantId(TENANT_ID);
        role.setStatus(1);
        return role;
    }

    private ResourceEntity createResourceEntity(Long id, String code) {
        ResourceEntity resource = new ResourceEntity();
        resource.setId(id);
        resource.setCode(code);
        resource.setTenantId(TENANT_ID);
        resource.setResourceType(RESOURCE_TYPE);
        resource.setStatus(1);
        return resource;
    }

    private OperationPermission createOperationPermission(Long id, Integer resourceType, String code, Long binaryBit) {
        OperationPermission op = new OperationPermission();
        op.setId(id);
        op.setResourceType(resourceType);
        op.setCode(code);
        op.setBinaryBit(binaryBit);
        op.setTenantId(TENANT_ID);
        return op;
    }

    private PermissionCondition createCondition(Long id, String code) {
        PermissionCondition condition = new PermissionCondition();
        condition.setId(id);
        condition.setCode(code);
        condition.setTenantId(TENANT_ID);
        condition.setEnabled(true);
        return condition;
    }
}