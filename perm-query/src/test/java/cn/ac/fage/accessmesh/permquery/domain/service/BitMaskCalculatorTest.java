package cn.ac.fage.accessmesh.permquery.domain.service;

import cn.ac.fage.accessmesh.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.permission.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.permquery.domain.model.valueobject.BitMask;
import cn.ac.fage.accessmesh.permquery.infrastructure.adapter.EntityBatchLoadAdapter;
import cn.ac.fage.accessmesh.permquery.infrastructure.adapter.PermCacheAdapter;
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
 * 位掩码计算领域服务单元测试
 * <p>
 * 测试 BitMaskCalculator 核心算法，覆盖：
 * 1. 正常计算流程（多个资源类型、多个操作）
 * 2. 空输入返回空结果
 * 3. 单个资源类型计算
 * 4. 位掩码覆盖关系计算
 * 5. 缓存命中和未命中场景
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class BitMaskCalculatorTest {

    /** 权限缓存适配器 Mock */
    @Mock
    private PermCacheAdapter permCacheAdapter;

    /** 实体批量加载适配器 Mock */
    @Mock
    private EntityBatchLoadAdapter entityBatchLoadAdapter;

    /** 操作权限 Mapper Mock */
    @Mock
    private OperationPermissionMapper operationPermissionMapper;

    /** 待测试的位掩码计算器实例 */
    private BitMaskCalculator calculator;

    /** 测试租户ID */
    private static final Long TENANT_ID = 1L;

    /** 测试资源类型值 */
    private static final Integer RESOURCE_TYPE_API = 1;
    private static final Integer RESOURCE_TYPE_MENU = 2;
    private static final Integer RESOURCE_TYPE_DATA = 3;

    /** 测试操作ID */
    private static final Long OP_VIEW_ID = 100L;
    private static final Long OP_CREATE_ID = 101L;
    private static final Long OP_EDIT_ID = 102L;
    private static final Long OP_DELETE_ID = 103L;
    private static final Long OP_MANAGE_ID = 104L;

    /** 测试位值 */
    private static final Long BIT_VIEW = 1L;      // 2^0
    private static final Long BIT_CREATE = 2L;    // 2^1
    private static final Long BIT_EDIT = 4L;      // 2^2
    private static final Long BIT_DELETE = 8L;    // 2^3
    private static final Long BIT_MANAGE = 16L;   // 2^4 (包含所有权限)

    @BeforeEach
    void setUp() {
        calculator = new BitMaskCalculator(
            permCacheAdapter,
            entityBatchLoadAdapter,
            operationPermissionMapper
        );
    }

    // ========== 正常计算流程测试 ==========

    @Nested
    @DisplayName("正常位掩码计算测试")
    class NormalCalculationTests {

        @Test
        @DisplayName("计算单个资源类型的位掩码 - VIEW操作")
        void shouldCalculateBitMaskForSingleResourceTypeViewOperation() {
            // Given: 单个资源类型，VIEW操作
            Set<Integer> resourceTypes = Set.of(RESOURCE_TYPE_API);
            Set<Long> operationIds = Set.of(OP_VIEW_ID);

            // Mock 目标操作加载
            OperationPermission viewOp = createOperationPermission(OP_VIEW_ID, RESOURCE_TYPE_API, "VIEW", BIT_VIEW, 0L);
            when(entityBatchLoadAdapter.batchLoadOperations(TENANT_ID, operationIds))
                .thenReturn(Map.of(OP_VIEW_ID, viewOp));

            // Mock 该资源类型的所有操作权限（缓存命中）
            OperationPermission viewOpWithCache = createOperationPermission(OP_VIEW_ID, RESOURCE_TYPE_API, "VIEW", BIT_VIEW, 0L);
            OperationPermission createOp = createOperationPermission(OP_CREATE_ID, RESOURCE_TYPE_API, "CREATE", BIT_CREATE, 0L);
            when(permCacheAdapter.getOperationPermissionsByType(TENANT_ID, RESOURCE_TYPE_API))
                .thenReturn(Map.of(OP_VIEW_ID, viewOpWithCache, OP_CREATE_ID, createOp));

            // When: 计算位掩码
            Map<Integer, Long> result = calculator.calculate(TENANT_ID, resourceTypes, operationIds);

            // Then: 验证结果
            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals(BIT_VIEW, result.get(RESOURCE_TYPE_API));

            // 验证调用
            verify(entityBatchLoadAdapter).batchLoadOperations(TENANT_ID, operationIds);
            verify(permCacheAdapter).getOperationPermissionsByType(TENANT_ID, RESOURCE_TYPE_API);
        }

        @Test
        @DisplayName("计算位掩码 - MANAGE操作包含所有子操作位")
        void shouldCalculateBitMaskForManageOperationThatCoversAllSubOperations() {
            // Given: MANAGE操作（有继承掩码）
            Set<Integer> resourceTypes = Set.of(RESOURCE_TYPE_API);
            Set<Long> operationIds = Set.of(OP_MANAGE_ID);

            // Mock 目标操作加载 - MANAGE包含VIEW|CREATE|EDIT|DELETE
            OperationPermission manageOp = createOperationPermission(
                OP_MANAGE_ID, RESOURCE_TYPE_API, "MANAGE", BIT_MANAGE,
                BIT_VIEW | BIT_CREATE | BIT_EDIT | BIT_DELETE // inheritMask
            );
            when(entityBatchLoadAdapter.batchLoadOperations(TENANT_ID, operationIds))
                .thenReturn(Map.of(OP_MANAGE_ID, manageOp));

            // Mock 该资源类型的所有操作权限
            Map<Long, OperationPermission> allOps = Map.of(
                OP_VIEW_ID, createOperationPermission(OP_VIEW_ID, RESOURCE_TYPE_API, "VIEW", BIT_VIEW, 0L),
                OP_CREATE_ID, createOperationPermission(OP_CREATE_ID, RESOURCE_TYPE_API, "CREATE", BIT_CREATE, 0L),
                OP_EDIT_ID, createOperationPermission(OP_EDIT_ID, RESOURCE_TYPE_API, "EDIT", BIT_EDIT, 0L),
                OP_DELETE_ID, createOperationPermission(OP_DELETE_ID, RESOURCE_TYPE_API, "DELETE", BIT_DELETE, 0L),
                OP_MANAGE_ID, manageOp
            );
            when(permCacheAdapter.getOperationPermissionsByType(TENANT_ID, RESOURCE_TYPE_API))
                .thenReturn(allOps);

            // When: 计算位掩码
            Map<Integer, Long> result = calculator.calculate(TENANT_ID, resourceTypes, operationIds);

            // Then: 位掩码应包含所有能覆盖VIEW的操作位
            // MANAGE(16)有效位=16|15=31，覆盖VIEW(1) -> BIT_MANAGE|BIT_VIEW|...都包含
            assertNotNull(result);
            // 期望：BIT_VIEW | BIT_MANAGE = 17 (因为VIEW有效位=1覆盖VIEW，MANAGE有效位=31也覆盖VIEW)
            assertTrue((result.get(RESOURCE_TYPE_API) & BIT_VIEW) != 0);
            assertTrue((result.get(RESOURCE_TYPE_API) & BIT_MANAGE) != 0);
        }

        @Test
        @DisplayName("计算多个资源类型的位掩码")
        void shouldCalculateBitMaskForMultipleResourceTypes() {
            // Given: 多个资源类型
            Set<Integer> resourceTypes = Set.of(RESOURCE_TYPE_API, RESOURCE_TYPE_MENU);
            Set<Long> operationIds = Set.of(OP_VIEW_ID);

            // Mock 目标操作加载
            OperationPermission viewApiOp = createOperationPermission(OP_VIEW_ID, RESOURCE_TYPE_API, "VIEW", BIT_VIEW, 0L);
            when(entityBatchLoadAdapter.batchLoadOperations(TENANT_ID, operationIds))
                .thenReturn(Map.of(OP_VIEW_ID, viewApiOp));

            // Mock API类型的操作权限
            when(permCacheAdapter.getOperationPermissionsByType(TENANT_ID, RESOURCE_TYPE_API))
                .thenReturn(Map.of(OP_VIEW_ID, viewApiOp));

            // Mock MENU类型的操作权限（VIEW操作也在该类型下）
            OperationPermission viewMenuOp = createOperationPermission(200L, RESOURCE_TYPE_MENU, "VIEW", BIT_VIEW, 0L);
            when(permCacheAdapter.getOperationPermissionsByType(TENANT_ID, RESOURCE_TYPE_MENU))
                .thenReturn(Map.of(200L, viewMenuOp));

            // When: 计算位掩码
            Map<Integer, Long> result = calculator.calculate(TENANT_ID, resourceTypes, operationIds);

            // Then: 验证两个资源类型都有结果
            assertNotNull(result);
            // API类型有VIEW操作
            assertEquals(BIT_VIEW, result.get(RESOURCE_TYPE_API));
            // MENU类型无OP_VIEW_ID（目标操作不属于该类型），应该被过滤掉
            // 但由于目标操作resourceType=API，MENU类型不匹配，所以MENU类型无掩码
            assertTrue(result.containsKey(RESOURCE_TYPE_API));
            // MENU类型目标操作不匹配，所以没有结果或结果为空
        }

        @Test
        @DisplayName("计算多个操作的合并位掩码")
        void shouldCalculateCombinedBitMaskForMultipleOperations() {
            // Given: 多个操作
            Set<Integer> resourceTypes = Set.of(RESOURCE_TYPE_API);
            Set<Long> operationIds = Set.of(OP_VIEW_ID, OP_CREATE_ID);

            // Mock 目标操作加载
            OperationPermission viewOp = createOperationPermission(OP_VIEW_ID, RESOURCE_TYPE_API, "VIEW", BIT_VIEW, 0L);
            OperationPermission createOp = createOperationPermission(OP_CREATE_ID, RESOURCE_TYPE_API, "CREATE", BIT_CREATE, 0L);
            when(entityBatchLoadAdapter.batchLoadOperations(TENANT_ID, operationIds))
                .thenReturn(Map.of(OP_VIEW_ID, viewOp, OP_CREATE_ID, createOp));

            // Mock 该资源类型的所有操作权限
            Map<Long, OperationPermission> allOps = Map.of(
                OP_VIEW_ID, viewOp,
                OP_CREATE_ID, createOp,
                OP_EDIT_ID, createOperationPermission(OP_EDIT_ID, RESOURCE_TYPE_API, "EDIT", BIT_EDIT, 0L),
                OP_MANAGE_ID, createOperationPermission(OP_MANAGE_ID, RESOURCE_TYPE_API, "MANAGE", BIT_MANAGE, BIT_VIEW | BIT_CREATE | BIT_EDIT | BIT_DELETE)
            );
            when(permCacheAdapter.getOperationPermissionsByType(TENANT_ID, RESOURCE_TYPE_API))
                .thenReturn(allOps);

            // When: 计算位掩码
            Map<Integer, Long> result = calculator.calculate(TENANT_ID, resourceTypes, operationIds);

            // Then: 位掩码应包含所有能覆盖VIEW和CREATE的操作位
            assertNotNull(result);
            long mask = result.get(RESOURCE_TYPE_API);
            // VIEW(1) -> 所有有效位&1!=0的操作位加入掩码
            // CREATE(2) -> 所有有效位&2!=0的操作位加入掩码
            // 至少包含BIT_VIEW和BIT_CREATE
            assertTrue((mask & BIT_VIEW) != 0);
            assertTrue((mask & BIT_CREATE) != 0);
        }

        @Test
        @DisplayName("calculateForType - 单个资源类型计算")
        void shouldCalculateBitMaskForSingleType() {
            // Given: 单个资源类型
            Set<Long> operationIds = Set.of(OP_VIEW_ID);

            // Mock 目标操作加载
            OperationPermission viewOp = createOperationPermission(OP_VIEW_ID, RESOURCE_TYPE_API, "VIEW", BIT_VIEW, 0L);
            when(entityBatchLoadAdapter.batchLoadOperations(TENANT_ID, operationIds))
                .thenReturn(Map.of(OP_VIEW_ID, viewOp));

            // Mock 缓存
            when(permCacheAdapter.getOperationPermissionsByType(TENANT_ID, RESOURCE_TYPE_API))
                .thenReturn(Map.of(OP_VIEW_ID, viewOp));

            // When: 使用calculateForType方法
            BitMask result = calculator.calculateForType(TENANT_ID, RESOURCE_TYPE_API, operationIds);

            // Then: 返回BitMask值对象
            assertNotNull(result);
            assertEquals(BIT_VIEW, result.raw());
            assertFalse(result.isEmpty());
        }
    }

    // ========== 空输入返回空结果测试 ==========

    @Nested
    @DisplayName("空输入返回空结果测试")
    class EmptyInputTests {

        @Test
        @DisplayName("tenantId为null - 返回空结果")
        void shouldReturnEmptyWhenTenantIdIsNull() {
            // Given: tenantId为null
            Set<Integer> resourceTypes = Set.of(RESOURCE_TYPE_API);
            Set<Long> operationIds = Set.of(OP_VIEW_ID);

            // When: 计算位掩码
            Map<Integer, Long> result = calculator.calculate(null, resourceTypes, operationIds);

            // Then: 返回空结果
            assertTrue(result.isEmpty());
            verify(entityBatchLoadAdapter, never()).batchLoadOperations(anyLong(), anySet());
        }

        @Test
        @DisplayName("resourceTypes为null - 返回空结果")
        void shouldReturnEmptyWhenResourceTypesIsNull() {
            // Given: resourceTypes为null
            Set<Long> operationIds = Set.of(OP_VIEW_ID);

            // When: 计算位掩码
            Map<Integer, Long> result = calculator.calculate(TENANT_ID, null, operationIds);

            // Then: 返回空结果
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("resourceTypes为空 - 返回空结果")
        void shouldReturnEmptyWhenResourceTypesIsEmpty() {
            // Given: resourceTypes为空
            Set<Long> operationIds = Set.of(OP_VIEW_ID);

            // When: 计算位掩码
            Map<Integer, Long> result = calculator.calculate(TENANT_ID, Collections.emptySet(), operationIds);

            // Then: 返回空结果
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("operationIds为null - 返回空结果")
        void shouldReturnEmptyWhenOperationIdsIsNull() {
            // Given: operationIds为null
            Set<Integer> resourceTypes = Set.of(RESOURCE_TYPE_API);

            // When: 计算位掩码
            Map<Integer, Long> result = calculator.calculate(TENANT_ID, resourceTypes, null);

            // Then: 返回空结果
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("operationIds为空 - 返回空结果")
        void shouldReturnEmptyWhenOperationIdsIsEmpty() {
            // Given: operationIds为空
            Set<Integer> resourceTypes = Set.of(RESOURCE_TYPE_API);

            // When: 计算位掩码
            Map<Integer, Long> result = calculator.calculate(TENANT_ID, resourceTypes, Collections.emptySet());

            // Then: 返回空结果
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("目标操作未找到 - 返回空结果")
        void shouldReturnEmptyWhenTargetOperationsNotFound() {
            // Given: 目标操作不存在
            Set<Integer> resourceTypes = Set.of(RESOURCE_TYPE_API);
            Set<Long> operationIds = Set.of(OP_VIEW_ID);

            when(entityBatchLoadAdapter.batchLoadOperations(TENANT_ID, operationIds))
                .thenReturn(Collections.emptyMap());

            // When: 计算位掩码
            Map<Integer, Long> result = calculator.calculate(TENANT_ID, resourceTypes, operationIds);

            // Then: 返回空结果
            assertTrue(result.isEmpty());
            verify(permCacheAdapter, never()).getOperationPermissionsByType(anyLong(), any());
        }

        @Test
        @DisplayName("calculateForType - 空输入返回零掩码")
        void shouldReturnZeroBitMaskForEmptyInput() {
            // When: 各种空输入
            BitMask result1 = calculator.calculateForType(null, RESOURCE_TYPE_API, Set.of(OP_VIEW_ID));
            BitMask result2 = calculator.calculateForType(TENANT_ID, null, Set.of(OP_VIEW_ID));
            BitMask result3 = calculator.calculateForType(TENANT_ID, RESOURCE_TYPE_API, null);
            BitMask result4 = calculator.calculateForType(TENANT_ID, RESOURCE_TYPE_API, Collections.emptySet());

            // Then: 都返回零掩码
            assertTrue(result1.isEmpty());
            assertTrue(result2.isEmpty());
            assertTrue(result3.isEmpty());
            assertTrue(result4.isEmpty());
            assertEquals(BitMask.zero(), result1);
            assertEquals(BitMask.zero(), result2);
            assertEquals(BitMask.zero(), result3);
            assertEquals(BitMask.zero(), result4);
        }
    }

    // ========== 缓存相关测试 ==========

    @Nested
    @DisplayName("缓存相关测试")
    class CacheTests {

        @Test
        @DisplayName("缓存命中 - 不查询数据库")
        void shouldUseCacheWhenCacheHit() {
            // Given: 缓存已存在
            Set<Integer> resourceTypes = Set.of(RESOURCE_TYPE_API);
            Set<Long> operationIds = Set.of(OP_VIEW_ID);

            OperationPermission viewOp = createOperationPermission(OP_VIEW_ID, RESOURCE_TYPE_API, "VIEW", BIT_VIEW, 0L);
            when(entityBatchLoadAdapter.batchLoadOperations(TENANT_ID, operationIds))
                .thenReturn(Map.of(OP_VIEW_ID, viewOp));

            // Mock 缓存命中
            Map<Long, OperationPermission> cachedOps = Map.of(
                OP_VIEW_ID, viewOp,
                OP_CREATE_ID, createOperationPermission(OP_CREATE_ID, RESOURCE_TYPE_API, "CREATE", BIT_CREATE, 0L)
            );
            when(permCacheAdapter.getOperationPermissionsByType(TENANT_ID, RESOURCE_TYPE_API))
                .thenReturn(cachedOps);

            // When: 计算位掩码
            Map<Integer, Long> result = calculator.calculate(TENANT_ID, resourceTypes, operationIds);

            // Then: 使用缓存，不查询数据库
            assertNotNull(result);
            verify(permCacheAdapter).getOperationPermissionsByType(TENANT_ID, RESOURCE_TYPE_API);
            verify(operationPermissionMapper, never()).selectByTenantAndResourceType(anyLong(), any());
        }

        @Test
        @DisplayName("缓存未命中 - 从数据库加载并写入缓存")
        void shouldLoadFromDbAndCacheWhenCacheMiss() {
            // Given: 缓存不存在
            Set<Integer> resourceTypes = Set.of(RESOURCE_TYPE_API);
            Set<Long> operationIds = Set.of(OP_VIEW_ID);

            OperationPermission viewOp = createOperationPermission(OP_VIEW_ID, RESOURCE_TYPE_API, "VIEW", BIT_VIEW, 0L);
            when(entityBatchLoadAdapter.batchLoadOperations(TENANT_ID, operationIds))
                .thenReturn(Map.of(OP_VIEW_ID, viewOp));

            // Mock 缓存未命中
            when(permCacheAdapter.getOperationPermissionsByType(TENANT_ID, RESOURCE_TYPE_API))
                .thenReturn(null);

            // Mock 数据库查询
            List<OperationPermission> dbOps = List.of(
                viewOp,
                createOperationPermission(OP_CREATE_ID, RESOURCE_TYPE_API, "CREATE", BIT_CREATE, 0L)
            );
            when(operationPermissionMapper.selectByTenantAndResourceType(TENANT_ID, RESOURCE_TYPE_API))
                .thenReturn(dbOps);

            // When: 计算位掩码
            Map<Integer, Long> result = calculator.calculate(TENANT_ID, resourceTypes, operationIds);

            // Then: 从数据库加载并写入缓存
            assertNotNull(result);
            verify(operationPermissionMapper).selectByTenantAndResourceType(TENANT_ID, RESOURCE_TYPE_API);
            verify(permCacheAdapter).putOperationPermissionsByType(eq(TENANT_ID), eq(RESOURCE_TYPE_API), any());
        }

        @Test
        @DisplayName("缓存未命中但数据库也无数据 - 返回空")
        void shouldReturnEmptyWhenCacheMissAndDbEmpty() {
            // Given: 缓存和数据库都无数据
            Set<Integer> resourceTypes = Set.of(RESOURCE_TYPE_API);
            Set<Long> operationIds = Set.of(OP_VIEW_ID);

            OperationPermission viewOp = createOperationPermission(OP_VIEW_ID, RESOURCE_TYPE_API, "VIEW", BIT_VIEW, 0L);
            when(entityBatchLoadAdapter.batchLoadOperations(TENANT_ID, operationIds))
                .thenReturn(Map.of(OP_VIEW_ID, viewOp));

            // Mock 缓存未命中
            when(permCacheAdapter.getOperationPermissionsByType(TENANT_ID, RESOURCE_TYPE_API))
                .thenReturn(null);

            // Mock 数据库查询返回空
            when(operationPermissionMapper.selectByTenantAndResourceType(TENANT_ID, RESOURCE_TYPE_API))
                .thenReturn(List.of());

            // When: 计算位掩码
            Map<Integer, Long> result = calculator.calculate(TENANT_ID, resourceTypes, operationIds);

            // Then: 该资源类型无结果
            assertTrue(result.isEmpty() || !result.containsKey(RESOURCE_TYPE_API));
        }

        @Test
        @DisplayName("失效缓存 - 调用适配器失效方法")
        void shouldInvalidateCache() {
            // When: 失效缓存
            calculator.invalidateCache(TENANT_ID, RESOURCE_TYPE_API);

            // Then: 调用适配器失效方法
            verify(permCacheAdapter).evictOperationPermissionsByType(TENANT_ID, RESOURCE_TYPE_API);
        }

        @Test
        @DisplayName("失效缓存 - 参数为null不执行")
        void shouldNotInvalidateCacheWhenParamsNull() {
            // When: 参数为null
            calculator.invalidateCache(null, RESOURCE_TYPE_API);
            calculator.invalidateCache(TENANT_ID, null);

            // Then: 不执行失效
            verify(permCacheAdapter, never()).evictOperationPermissionsByType(anyLong(), any());
        }
    }

    // ========== 位掩码覆盖关系测试 ==========

    @Nested
    @DisplayName("位掩码覆盖关系测试")
    class CoverageTests {

        @Test
        @DisplayName("effectiveBits - 计算有效位掩码")
        void shouldCalculateEffectiveBits() {
            // Given: 操作权限有继承掩码
            OperationPermission op = createOperationPermission(OP_MANAGE_ID, RESOURCE_TYPE_API, "MANAGE", BIT_MANAGE, BIT_VIEW | BIT_CREATE);

            // When: 计算有效位
            long effectiveBits = calculator.effectiveBits(op);

            // Then: 有效位 = binaryBit | inheritMask
            assertEquals(BIT_MANAGE | BIT_VIEW | BIT_CREATE, effectiveBits);
            assertEquals(16 | 1 | 2, effectiveBits);
        }

        @Test
        @DisplayName("effectiveBits - 无继承掩码时仅返回binaryBit")
        void shouldReturnBinaryBitWhenNoInheritMask() {
            // Given: 操作权限无继承掩码
            OperationPermission op = createOperationPermission(OP_VIEW_ID, RESOURCE_TYPE_API, "VIEW", BIT_VIEW, 0L);

            // When: 计算有效位
            long effectiveBits = calculator.effectiveBits(op);

            // Then: 有效位 = binaryBit
            assertEquals(BIT_VIEW, effectiveBits);
        }

        @Test
        @DisplayName("covers - 判断授予权限覆盖目标权限")
        void shouldJudgeCoverageCorrectly() {
            // Given: 授予MANAGE，目标VIEW
            OperationPermission granted = createOperationPermission(OP_MANAGE_ID, RESOURCE_TYPE_API, "MANAGE", BIT_MANAGE, BIT_VIEW | BIT_CREATE);
            OperationPermission target = createOperationPermission(OP_VIEW_ID, RESOURCE_TYPE_API, "VIEW", BIT_VIEW, 0L);

            // When: 判断覆盖
            boolean covers = calculator.covers(granted, target);

            // Then: MANAGE覆盖VIEW（因为MANAGE继承掩码包含VIEW）
            assertTrue(covers);
        }

        @Test
        @DisplayName("covers - 判断不覆盖")
        void shouldJudgeNotCoverCorrectly() {
            // Given: 授予VIEW，目标CREATE
            OperationPermission granted = createOperationPermission(OP_VIEW_ID, RESOURCE_TYPE_API, "VIEW", BIT_VIEW, 0L);
            OperationPermission target = createOperationPermission(OP_CREATE_ID, RESOURCE_TYPE_API, "CREATE", BIT_CREATE, 0L);

            // When: 判断覆盖
            boolean covers = calculator.covers(granted, target);

            // Then: VIEW不覆盖CREATE
            assertFalse(covers);
        }

        @Test
        @DisplayName("covers - null参数返回false")
        void shouldReturnFalseWhenNullParams() {
            // When: null参数
            OperationPermission validOp = createOperationPermission(OP_VIEW_ID, RESOURCE_TYPE_API, "VIEW", BIT_VIEW, 0L);

            boolean result1 = calculator.covers(null, validOp);
            boolean result2 = calculator.covers(validOp, null);
            boolean result3 = calculator.covers(null, null);

            // Then: 返回false
            assertFalse(result1);
            assertFalse(result2);
            assertFalse(result3);
        }
    }

    // ========== getOperationPermissionsByType 测试 ==========

    @Nested
    @DisplayName("getOperationPermissionsByType 测试")
    class GetOperationsByTypeTests {

        @Test
        @DisplayName("正常获取操作权限映射")
        void shouldGetOperationPermissionsByType() {
            // Given: 缓存命中
            OperationPermission viewOp = createOperationPermission(OP_VIEW_ID, RESOURCE_TYPE_API, "VIEW", BIT_VIEW, 0L);
            OperationPermission createOp = createOperationPermission(OP_CREATE_ID, RESOURCE_TYPE_API, "CREATE", BIT_CREATE, 0L);
            Map<Long, OperationPermission> cachedOps = Map.of(OP_VIEW_ID, viewOp, OP_CREATE_ID, createOp);

            when(permCacheAdapter.getOperationPermissionsByType(TENANT_ID, RESOURCE_TYPE_API))
                .thenReturn(cachedOps);

            // When: 获取操作权限
            Map<Long, OperationPermission> result = calculator.getOperationPermissionsByType(TENANT_ID, RESOURCE_TYPE_API);

            // Then: 返回缓存数据
            assertEquals(2, result.size());
            assertEquals(viewOp, result.get(OP_VIEW_ID));
            assertEquals(createOp, result.get(OP_CREATE_ID));
        }

        @Test
        @DisplayName("参数为null返回空Map")
        void shouldReturnEmptyWhenParamsNull() {
            // When: 参数为null
            Map<Long, OperationPermission> result1 = calculator.getOperationPermissionsByType(null, RESOURCE_TYPE_API);
            Map<Long, OperationPermission> result2 = calculator.getOperationPermissionsByType(TENANT_ID, null);

            // Then: 返回空Map
            assertTrue(result1.isEmpty());
            assertTrue(result2.isEmpty());
        }

        @Test
        @DisplayName("缓存未命中从数据库加载")
        void shouldLoadFromDatabaseWhenCacheMiss() {
            // Given: 缓存未命中
            when(permCacheAdapter.getOperationPermissionsByType(TENANT_ID, RESOURCE_TYPE_API))
                .thenReturn(null);

            OperationPermission viewOp = createOperationPermission(OP_VIEW_ID, RESOURCE_TYPE_API, "VIEW", BIT_VIEW, 0L);
            OperationPermission createOp = createOperationPermission(OP_CREATE_ID, RESOURCE_TYPE_API, "CREATE", BIT_CREATE, 0L);
            when(operationPermissionMapper.selectByTenantAndResourceType(TENANT_ID, RESOURCE_TYPE_API))
                .thenReturn(List.of(viewOp, createOp));

            // When: 获取操作权限
            Map<Long, OperationPermission> result = calculator.getOperationPermissionsByType(TENANT_ID, RESOURCE_TYPE_API);

            // Then: 从数据库加载
            assertEquals(2, result.size());
            verify(operationPermissionMapper).selectByTenantAndResourceType(TENANT_ID, RESOURCE_TYPE_API);
            verify(permCacheAdapter).putOperationPermissionsByType(TENANT_ID, RESOURCE_TYPE_API, result);
        }
    }

    // ========== 辅助方法 ==========

    private OperationPermission createOperationPermission(Long id, Integer resourceType, String code, Long binaryBit, Long inheritMask) {
        OperationPermission op = new OperationPermission();
        op.setId(id);
        op.setTenantId(TENANT_ID);
        op.setResourceType(resourceType);
        op.setCode(code);
        op.setBinaryBit(binaryBit);
        op.setInheritMask(inheritMask);
        return op;
    }
}