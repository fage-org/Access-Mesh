package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.access.infrastructure.aop.OperationLogRuntimeContext;
import cn.ac.fage.accessmesh.access.permission.dto.req.DependencyBatchSyncReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceDependencyCreateReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceDependencyUpdateReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceResolveKey;
import cn.ac.fage.accessmesh.access.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.access.permission.entity.ResourceDependency;
import cn.ac.fage.accessmesh.access.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.access.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.permission.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.ResourceDependencyMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.access.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.access.permission.util.OperatorContext;
import com.mybatisflex.core.update.UpdateWrapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * T-PERM-031 资源依赖后端收口用例集：
 * <ul>
 * <li>autoGrant 预留禁用（2026-08-27 设计定案）：所有写入口拒绝 true（20048）、省略落 false——
 *     旧实现（null 默认 true、true 静默入库）在本组用例下必然失败。</li>
 * <li>门禁五档类型级：读 list/graph/check = VIEW（原三读端点无门禁）、update/remove 从
 *     实例级收窄类型级（先解析后门禁，未知 id 优先 20019）。</li>
 * <li>update PUT 全量覆盖（UpdateEntity 显式写列，null 语义可达：资源对可改、
 *     sourceOperationBits=null=任意触发、description=null=清空；maintainSource 来源归属不动）。</li>
 * <li>等价重复预查 20054（同源+同目标+同 COALESCE(source_bits,0)，uk 兜底 DIVE 转译）。</li>
 * <li>操作码 fail-closed 20005（原静默丢弃：部分丢码合并已知位、全 miss 落 0——
 *     本组用例锁住「拼错码必须报错」）。</li>
 * <li>batch-sync FULL diff 三元组匹配（同资源对不同触发操作是不同规则，仅按资源对匹配
 *     会漏删/uk 冲突）+ 操作位循环外按类型批量解析（N+1 消解）。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class DependencyAppServiceImplTest {

    private static final Long TENANT = 1L;
    private static final Long OPERATOR = 100L;
    private static final Long SOURCE_ID = 11L;
    private static final Long TARGET_ID = 22L;

    @Mock private ResourceDependencyMapper dependencyMapper;
    @Mock private ResourceEntityMapper resourceEntityMapper;
    @Mock private OperationPermissionMapper operationPermissionMapper;
    @Mock private TypeResolutionService typeResolutionService;
    @Mock private PermQueryEngine engine;

    private DependencyAppServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DependencyAppServiceImpl(dependencyMapper, resourceEntityMapper,
            operationPermissionMapper, typeResolutionService, engine);
    }

    // ========== 辅助 ==========

    private void stubTypeLevelPermission(String operationCode, boolean allowed) {
        when(engine.hasPermissionByCode(eq(TENANT), anyLong(), eq(ResourceTypeCode.DEPENDENCY),
            isNull(), eq(operationCode))).thenReturn(allowed);
    }

    private ResourceDependencyCreateReq createReq(List<String> sourceCodes, List<String> requiredCodes,
                                                  Boolean autoGrant) {
        return new ResourceDependencyCreateReq("MENU", "menu:sys", null, sourceCodes,
            "API", "api:hello", null, requiredCodes, autoGrant, "test dependency");
    }

    private ResourceDependencyUpdateReq updateReq(List<String> sourceCodes, List<String> requiredCodes,
                                                  Boolean autoGrant, String description) {
        return new ResourceDependencyUpdateReq(7L, "MENU", "menu:sys", null, sourceCodes,
            "API", "api:hello", null, requiredCodes, autoGrant, description);
    }

    /** 覆盖 update 全链路的 stub：类型级 UPDATE 门禁 + 既有行存在 + 资源可解析 + 操作码可解析 + 无重复 */
    private void stubUpdateHappyPath(Long existingSourceBits) {
        stubTypeLevelPermission(OperationCodeConstants.UPDATE, true);
        ResourceDependency existing = newDep(7L, SOURCE_ID, TARGET_ID, existingSourceBits, 2L);
        when(dependencyMapper.selectOneById(7L)).thenReturn(existing);
        when(typeResolutionService.resolveResourceId(TENANT, "MENU", "menu:sys", null, null)).thenReturn(SOURCE_ID);
        when(typeResolutionService.resolveResourceId(TENANT, "API", "api:hello", null, null)).thenReturn(TARGET_ID);
        stubOperationResolution("API", Map.of("ACCESS", 41L), Map.of(41L, 4L));
        when(dependencyMapper.selectBySourceAndTargetIds(eq(TENANT), anySet(), anySet())).thenReturn(List.of());
    }

    /** stub 操作码解析链：code -> opId，opId -> binaryBit */
    private void stubOperationResolution(String typeCode, Map<String, Long> codeToId, Map<Long, Long> idToBit) {
        when(typeResolutionService.batchResolveOperationIds(eq(TENANT), eq(typeCode), anySet()))
            .thenReturn(codeToId);
        when(operationPermissionMapper.selectValidByIds(eq(TENANT), anySet()))
            .thenReturn(idToBit.entrySet().stream().map(e -> {
                OperationPermission op = new OperationPermission();
                op.setId(e.getKey());
                op.setBinaryBit(e.getValue());
                return op;
            }).toList());
    }

    private static ResourceDependency newDep(Long id, Long sourceId, Long targetId, Long sourceBits, Long requiredBits) {
        ResourceDependency dep = new ResourceDependency();
        dep.setId(id);
        dep.setTenantId(TENANT);
        dep.setResourceEntityId(sourceId);
        dep.setDependsOnResourceEntityId(targetId);
        dep.setSourceOperationBits(sourceBits);
        dep.setRequiredOperationBits(requiredBits);
        dep.setAutoGrant(false);
        dep.setDeleteFlag(0L);
        return dep;
    }

    private static ResourceEntity newResource(Long id, String code, String name, Integer typeValue) {
        ResourceEntity entity = new ResourceEntity();
        entity.setId(id);
        entity.setTenantId(TENANT);
        entity.setCode(code);
        entity.setName(name);
        entity.setResourceType(typeValue);
        return entity;
    }

    // ========== autoGrant 预留禁用（2026-08-27 设计定案） ==========

    @Nested
    class AutoGrantGuard {

        @Test
        void shouldRejectAutoGrantTrueOnCreate() {
            stubTypeLevelPermission(OperationCodeConstants.CREATE, true);

            BizException ex = assertThrows(BizException.class,
                () -> service.createDependency(TENANT, createReq(null, List.of("ACCESS"), true), OPERATOR));

            assertEquals(PermissionErrorCode.AUTO_GRANT_NOT_SUPPORTED.getCode(), ex.getErrorCode());
            verify(dependencyMapper, never()).insert(any(ResourceDependency.class));
        }

        @Test
        void shouldDefaultAutoGrantToFalseAndStampAdminUiOnCreate() {
            stubTypeLevelPermission(OperationCodeConstants.CREATE, true);
            when(typeResolutionService.resolveResourceId(TENANT, "MENU", "menu:sys", null, null)).thenReturn(SOURCE_ID);
            when(typeResolutionService.resolveResourceId(TENANT, "API", "api:hello", null, null)).thenReturn(TARGET_ID);
            stubOperationResolution("API", Map.of("ACCESS", 41L), Map.of(41L, 4L));
            when(dependencyMapper.selectBySourceAndTargetIds(eq(TENANT), anySet(), anySet())).thenReturn(List.of());
            when(resourceEntityMapper.selectValidByIds(eq(TENANT), anySet())).thenReturn(List.of());

            service.createDependency(TENANT, createReq(null, List.of("ACCESS"), null), OPERATOR);

            ArgumentCaptor<ResourceDependency> captor = ArgumentCaptor.forClass(ResourceDependency.class);
            verify(dependencyMapper).insert(captor.capture());
            assertThat(captor.getValue().getAutoGrant()).isFalse();
            // T-PERM-031：管理端创建行显式落 ADMIN_UI（schema 默认值不再依赖 insert 忽略 null 策略）
            assertThat(captor.getValue().getMaintainSource()).isEqualTo("ADMIN_UI");
            assertThat(captor.getValue().getOwnerServiceCode()).isNull();
        }

        @Test
        void shouldRejectAutoGrantTrueOnUpdate() {
            ResourceDependency existing = newDep(7L, SOURCE_ID, TARGET_ID, 2L, 2L);
            when(dependencyMapper.selectOneById(7L)).thenReturn(existing);
            stubTypeLevelPermission(OperationCodeConstants.UPDATE, true);

            BizException ex = assertThrows(BizException.class,
                () -> service.updateDependency(TENANT, updateReq(null, List.of("ACCESS"), Boolean.TRUE, null), OPERATOR));

            assertEquals(PermissionErrorCode.AUTO_GRANT_NOT_SUPPORTED.getCode(), ex.getErrorCode());
            verify(dependencyMapper, never()).update(any(ResourceDependency.class));
        }

        @Test
        void shouldRejectAutoGrantTrueOnBatchSyncItem() {
            stubTypeLevelPermission(OperationCodeConstants.SYNC, true);

            DependencyBatchSyncReq req = new DependencyBatchSyncReq("example-service", "SERVICE_SYNC",
                "INCREMENTAL", List.of(new DependencyBatchSyncReq.DependencySyncItem(
                    "REPORT", "report:sales", null, null,
                    "API", "api:report:sales:query", null, List.of("ACCESS"), Boolean.TRUE, "d")));

            BizException ex = assertThrows(BizException.class,
                () -> service.batchSyncDependencies(TENANT, req, OPERATOR));

            assertEquals(PermissionErrorCode.AUTO_GRANT_NOT_SUPPORTED.getCode(), ex.getErrorCode());
            verify(dependencyMapper, never()).insert(any(ResourceDependency.class));
            verify(dependencyMapper, never()).insertBatch(anyList());
        }

        @Test
        void shouldRejectAutoGrantTrueBeforeFullDiffDeletion() {
            stubTypeLevelPermission(OperationCodeConstants.SYNC, true);

            DependencyBatchSyncReq req = new DependencyBatchSyncReq("example-service", "SERVICE_SYNC",
                "FULL", List.of(new DependencyBatchSyncReq.DependencySyncItem(
                    "REPORT", "report:sales", null, null,
                    "API", "api:report:sales:query", null, List.of("ACCESS"), Boolean.TRUE, "d")));

            BizException ex = assertThrows(BizException.class,
                () -> service.batchSyncDependencies(TENANT, req, OPERATOR));

            assertEquals(PermissionErrorCode.AUTO_GRANT_NOT_SUPPORTED.getCode(), ex.getErrorCode());
            // 预检先于 FULL diff 全部数据库操作：存量查询与删除/写入均不得发生
            verify(dependencyMapper, never()).selectByOwnerService(anyLong(), anyString(), anyString());
            verify(dependencyMapper, never()).softDeleteBatch(anyLong(), anyList(), any());
            verify(dependencyMapper, never()).insert(any(ResourceDependency.class));
            verify(dependencyMapper, never()).insertBatch(anyList());
        }
    }

    // ========== 读门禁（T-PERM-031：原 list/graph/check 三端点无门禁） ==========

    @Nested
    class ReadEndpointsViewGate {

        @Test
        void shouldRejectListWithoutViewPermission() {
            try (MockedStatic<OperatorContext> opCtx = mockStatic(OperatorContext.class)) {
                opCtx.when(OperatorContext::getOperatorId).thenReturn(OPERATOR);
                stubTypeLevelPermission(OperationCodeConstants.VIEW, false);

                assertThrows(SecurityException.class, () -> service.listDependencies(TENANT, null));
                verify(dependencyMapper, never()).selectByTenantAndResourceEntityId(anyLong(), any());
            }
        }

        @Test
        void shouldRejectGraphFullListWithoutViewPermission() {
            try (MockedStatic<OperatorContext> opCtx = mockStatic(OperatorContext.class)) {
                opCtx.when(OperatorContext::getOperatorId).thenReturn(OPERATOR);
                stubTypeLevelPermission(OperationCodeConstants.VIEW, false);

                assertThrows(SecurityException.class, () -> service.listAllDependencies(TENANT));
                verify(dependencyMapper, never()).selectByTenantId(anyLong());
            }
        }

        @Test
        void shouldRejectCycleCheckWithoutViewPermission() {
            try (MockedStatic<OperatorContext> opCtx = mockStatic(OperatorContext.class)) {
                opCtx.when(OperatorContext::getOperatorId).thenReturn(OPERATOR);
                stubTypeLevelPermission(OperationCodeConstants.VIEW, false);

                assertThrows(SecurityException.class, () -> service.hasDependencyCycle(TENANT,
                    new cn.ac.fage.accessmesh.access.permission.dto.req.ResourceDependencyCheckReq(
                        "MENU", "menu:sys", null, "API", "api:hello", null)));
                verify(typeResolutionService, never()).resolveResourceId(anyLong(), anyString(), anyString(), any(), any());
            }
        }

        @Test
        void shouldBackfillTypeCodeAndNameInListResp() {
            try (MockedStatic<OperatorContext> opCtx = mockStatic(OperatorContext.class)) {
                opCtx.when(OperatorContext::getOperatorId).thenReturn(OPERATOR);
                stubTypeLevelPermission(OperationCodeConstants.VIEW, true);
                ResourceDependency dep = newDep(7L, SOURCE_ID, TARGET_ID, 2L, 4L);
                dep.setMaintainSource("ADMIN_UI");
                dep.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
                dep.setUpdatedAt(LocalDateTime.of(2026, 1, 2, 0, 0));
                when(dependencyMapper.selectByTenantId(TENANT)).thenReturn(List.of(dep));
                when(resourceEntityMapper.selectValidByIds(eq(TENANT), anySet())).thenReturn(List.of(
                    newResource(SOURCE_ID, "menu:sys", "系统菜单", 0),
                    newResource(TARGET_ID, "api:hello", "鉴权接口", 2)));
                when(typeResolutionService.batchResolveTypeCodes(eq(TENANT), eq("resource_type"), anySet()))
                    .thenReturn(Map.of(0, "MENU", 2, "API"));

                List<cn.ac.fage.accessmesh.access.permission.dto.resp.ResourceDependencyResp> resps =
                    service.listAllDependencies(TENANT);

                assertThat(resps).hasSize(1);
                var resp = resps.get(0);
                assertThat(resp.sourceResourceTypeCode()).isEqualTo("MENU");
                assertThat(resp.targetResourceTypeCode()).isEqualTo("API");
                assertThat(resp.sourceResourceName()).isEqualTo("系统菜单");
                assertThat(resp.targetResourceName()).isEqualTo("鉴权接口");
                assertThat(resp.maintainSource()).isEqualTo("ADMIN_UI");
                assertThat(resp.updatedAt()).isEqualTo(LocalDateTime.of(2026, 1, 2, 0, 0));
            }
        }
    }

    // ========== update：先解析后门禁 + PUT 全量覆盖 + 20054/20005/20044 ==========

    @Nested
    class UpdateSemantics {

        @Test
        void shouldThrow20019BeforePermissionCheckWhenDependencyMissing() {
            when(dependencyMapper.selectOneById(7L)).thenReturn(null);

            assertThatThrownBy(() -> service.updateDependency(TENANT,
                updateReq(null, List.of("ACCESS"), null, null), OPERATOR))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(PermissionErrorCode.DEPENDENCY_NOT_FOUND.getCode()));
            // 先解析后门禁（T-PERM-029 模式）：未知 id 优先 20019，权限引擎未被触达
            verifyNoInteractions(engine);
        }

        @Test
        void shouldRejectUpdateWithoutTypeLevelPermission() {
            when(dependencyMapper.selectOneById(7L)).thenReturn(newDep(7L, SOURCE_ID, TARGET_ID, 2L, 2L));
            stubTypeLevelPermission(OperationCodeConstants.UPDATE, false);

            assertThrows(SecurityException.class,
                () -> service.updateDependency(TENANT, updateReq(null, List.of("ACCESS"), null, null), OPERATOR));
            verify(dependencyMapper, never()).update(any(ResourceDependency.class));
        }

        @Test
        void shouldFullyOverwriteFieldsWithExplicitNullColumns() {
            stubUpdateHappyPath(2L);
            // re-select 返回更新后行（资源对已切换到 33->44，验证 PUT 资源对可改）
            ResourceDependency updated = newDep(7L, 33L, 44L, null, 4L);
            when(dependencyMapper.selectOneById(7L)).thenReturn(newDep(7L, SOURCE_ID, TARGET_ID, 2L, 2L), updated);
            when(resourceEntityMapper.selectValidByIds(eq(TENANT), anySet())).thenReturn(List.of());
            when(typeResolutionService.resolveResourceId(TENANT, "MENU", "menu:sys", null, null)).thenReturn(SOURCE_ID);
            when(typeResolutionService.resolveResourceId(TENANT, "API", "api:hello", null, null)).thenReturn(TARGET_ID);

            var resp = service.updateDependency(TENANT, updateReq(null, List.of("ACCESS"), null, null), OPERATOR);

            assertThat(resp.resourceEntityId()).isEqualTo(33L);
            ArgumentCaptor<ResourceDependency> captor = ArgumentCaptor.forClass(ResourceDependency.class);
            verify(dependencyMapper).update(captor.capture());
            // UpdateEntity「强制写列」核心语义：显式 set(null) 进 updates map → SET 子句
            @SuppressWarnings("unchecked")
            Map<String, Object> updates = ((UpdateWrapper<ResourceDependency>) captor.getValue()).getUpdates();
            assertThat(updates).containsKey("sourceOperationBits");
            assertThat(updates.get("sourceOperationBits")).isNull();
            assertThat(updates).containsKey("description");
            assertThat(updates.get("description")).isNull();
            assertThat(updates).containsKey("updatedBy");
            assertThat(updates).containsKey("updatedAt");
            // 来源归属不可变：管理端编辑不改写 maintainSource / ownerServiceCode
            assertThat(updates).doesNotContainKey("maintainSource");
            assertThat(updates).doesNotContainKey("ownerServiceCode");
        }

        @Test
        void shouldThrow20054WhenEquivalentDependencyExists() {
            ResourceDependency existing = newDep(7L, SOURCE_ID, TARGET_ID, 2L, 2L);
            when(dependencyMapper.selectOneById(7L)).thenReturn(existing);
            stubTypeLevelPermission(OperationCodeConstants.UPDATE, true);
            when(typeResolutionService.resolveResourceId(TENANT, "MENU", "menu:sys", null, null)).thenReturn(SOURCE_ID);
            when(typeResolutionService.resolveResourceId(TENANT, "API", "api:hello", null, null)).thenReturn(TARGET_ID);
            stubOperationResolution("API", Map.of("ACCESS", 41L), Map.of(41L, 4L));
            // 同资源对不同行的等价规则（NULL 与 0 同档：COALESCE 语义）
            when(dependencyMapper.selectBySourceAndTargetIds(eq(TENANT), anySet(), anySet()))
                .thenReturn(List.of(newDep(9L, SOURCE_ID, TARGET_ID, null, 2L)));

            assertThatThrownBy(() -> service.updateDependency(TENANT,
                updateReq(null, List.of("ACCESS"), null, null), OPERATOR))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(PermissionErrorCode.DEPENDENCY_DUPLICATE.getCode()));
        }

        @Test
        void shouldThrow20044WhenSourceEqualsTarget() {
            when(dependencyMapper.selectOneById(7L)).thenReturn(newDep(7L, SOURCE_ID, TARGET_ID, 2L, 2L));
            stubTypeLevelPermission(OperationCodeConstants.UPDATE, true);
            when(typeResolutionService.resolveResourceId(anyLong(), anyString(), anyString(), any(), any()))
                .thenReturn(SOURCE_ID);

            assertThatThrownBy(() -> service.updateDependency(TENANT,
                updateReq(null, List.of("ACCESS"), null, null), OPERATOR))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(PermissionErrorCode.INVALID_PARAM.getCode()));
        }

        @Test
        void shouldThrow20005WhenOperationCodeUnknown() {
            when(dependencyMapper.selectOneById(7L)).thenReturn(newDep(7L, SOURCE_ID, TARGET_ID, 2L, 2L));
            stubTypeLevelPermission(OperationCodeConstants.UPDATE, true);
            when(typeResolutionService.resolveResourceId(TENANT, "MENU", "menu:sys", null, null)).thenReturn(SOURCE_ID);
            when(typeResolutionService.resolveResourceId(TENANT, "API", "api:hello", null, null)).thenReturn(TARGET_ID);
            // 拼错码 ACCES 不解析：fail-closed 20005（原实现静默丢弃落 0）
            when(typeResolutionService.batchResolveOperationIds(eq(TENANT), eq("API"), anySet()))
                .thenReturn(Map.of());

            assertThatThrownBy(() -> service.updateDependency(TENANT,
                updateReq(null, List.of("ACCES"), null, null), OPERATOR))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(PermissionErrorCode.OPERATION_NOT_FOUND.getCode()));
            verify(dependencyMapper, never()).update(any(ResourceDependency.class));
        }

        @Test
        void shouldThrow20019WhenReselectEmptyAfterUpdate() {
            stubUpdateHappyPath(2L);
            when(dependencyMapper.selectOneById(7L)).thenReturn(newDep(7L, SOURCE_ID, TARGET_ID, 2L, 2L)).thenReturn(null);

            assertThatThrownBy(() -> service.updateDependency(TENANT,
                updateReq(null, List.of("ACCESS"), null, null), OPERATOR))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(PermissionErrorCode.DEPENDENCY_NOT_FOUND.getCode()));
        }

        @Test
        void shouldTranslateUniqueViolationTo20054OnCreate() {
            stubTypeLevelPermission(OperationCodeConstants.CREATE, true);
            when(typeResolutionService.resolveResourceId(TENANT, "MENU", "menu:sys", null, null)).thenReturn(SOURCE_ID);
            when(typeResolutionService.resolveResourceId(TENANT, "API", "api:hello", null, null)).thenReturn(TARGET_ID);
            stubOperationResolution("API", Map.of("ACCESS", 41L), Map.of(41L, 4L));
            when(dependencyMapper.selectBySourceAndTargetIds(eq(TENANT), anySet(), anySet())).thenReturn(List.of());
            when(dependencyMapper.insert(any(ResourceDependency.class)))
                .thenThrow(new DataIntegrityViolationException(
                    "ERROR: duplicate key value violates unique constraint \"uk_resource_dependency\""));

            assertThatThrownBy(() -> service.createDependency(TENANT, createReq(null, List.of("ACCESS"), null), OPERATOR))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(PermissionErrorCode.DEPENDENCY_DUPLICATE.getCode()));
        }
    }

    // ========== create：20054/20044/20005/空白码 ==========

    @Nested
    class CreateValidation {

        @Test
        void shouldThrow20054WhenDuplicatePrecheckHits() {
            stubTypeLevelPermission(OperationCodeConstants.CREATE, true);
            when(typeResolutionService.resolveResourceId(TENANT, "MENU", "menu:sys", null, null)).thenReturn(SOURCE_ID);
            when(typeResolutionService.resolveResourceId(TENANT, "API", "api:hello", null, null)).thenReturn(TARGET_ID);
            // 两个类型（MENU/API）各自 stub，操作位表一次返回全部（避免后一次 stub 覆盖前一次）
            when(typeResolutionService.batchResolveOperationIds(eq(TENANT), eq("MENU"), anySet()))
                .thenReturn(Map.of("VIEW", 31L));
            when(typeResolutionService.batchResolveOperationIds(eq(TENANT), eq("API"), anySet()))
                .thenReturn(Map.of("ACCESS", 41L));
            when(operationPermissionMapper.selectValidByIds(eq(TENANT), anySet()))
                .thenReturn(List.of(opOf(31L, 2L), opOf(41L, 4L)));
            when(dependencyMapper.selectBySourceAndTargetIds(eq(TENANT), anySet(), anySet()))
                .thenReturn(List.of(newDep(9L, SOURCE_ID, TARGET_ID, 2L, 2L)));

            assertThatThrownBy(() -> service.createDependency(TENANT,
                createReq(List.of("VIEW"), List.of("ACCESS"), null), OPERATOR))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(PermissionErrorCode.DEPENDENCY_DUPLICATE.getCode()));
            verify(dependencyMapper, never()).insert(any(ResourceDependency.class));
        }

        @Test
        void shouldThrow20044WhenCreateSelfDependency() {
            stubTypeLevelPermission(OperationCodeConstants.CREATE, true);
            when(typeResolutionService.resolveResourceId(anyLong(), anyString(), anyString(), any(), any()))
                .thenReturn(SOURCE_ID);

            assertThatThrownBy(() -> service.createDependency(TENANT,
                createReq(null, List.of("ACCESS"), null), OPERATOR))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(PermissionErrorCode.INVALID_PARAM.getCode()));
        }

        @Test
        void shouldThrow20005WhenCreateOperationCodePartiallyUnknown() {
            stubTypeLevelPermission(OperationCodeConstants.CREATE, true);
            when(typeResolutionService.resolveResourceId(TENANT, "MENU", "menu:sys", null, null)).thenReturn(SOURCE_ID);
            when(typeResolutionService.resolveResourceId(TENANT, "API", "api:hello", null, null)).thenReturn(TARGET_ID);
            // 部分解析成功：ACCESS 有、ACCES 无——原实现静默只合并已知位，现必须 fail-closed
            when(typeResolutionService.batchResolveOperationIds(eq(TENANT), eq("API"), anySet()))
                .thenReturn(Map.of("ACCESS", 41L));

            assertThatThrownBy(() -> service.createDependency(TENANT,
                createReq(null, List.of("ACCESS", "ACCES"), null), OPERATOR))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(PermissionErrorCode.OPERATION_NOT_FOUND.getCode()));
        }

        @Test
        void shouldThrow20044WhenAllOperationCodesBlank() {
            stubTypeLevelPermission(OperationCodeConstants.CREATE, true);
            when(typeResolutionService.resolveResourceId(TENANT, "MENU", "menu:sys", null, null)).thenReturn(SOURCE_ID);
            when(typeResolutionService.resolveResourceId(TENANT, "API", "api:hello", null, null)).thenReturn(TARGET_ID);

            assertThatThrownBy(() -> service.createDependency(TENANT,
                createReq(null, List.of("  "), null), OPERATOR))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(PermissionErrorCode.INVALID_PARAM.getCode()));
        }
    }

    // ========== remove：类型级全有或全无 + 幽灵 id 幂等跳过 ==========

    @Nested
    class RemoveSemantics {

        @Test
        void shouldRejectRemoveWithoutTypeLevelPermission() {
            stubTypeLevelPermission(OperationCodeConstants.DELETE, false);

            assertThrows(SecurityException.class,
                () -> service.deleteDependencies(TENANT, List.of(7L), OPERATOR));
            verify(dependencyMapper, never()).softDeleteBatch(anyLong(), anyList(), any());
        }

        @Test
        void shouldSkipAndMarkLogWhenAllGhostIds() {
            stubTypeLevelPermission(OperationCodeConstants.DELETE, true);
            when(dependencyMapper.selectValidByIds(eq(TENANT), anySet())).thenReturn(List.of());
            OperationLogRuntimeContext.clear();

            service.deleteDependencies(TENANT, List.of(999L), OPERATOR);

            verify(dependencyMapper, never()).softDeleteBatch(anyLong(), anyList(), any());
            assertThat(OperationLogRuntimeContext.snapshot().skip()).isTrue();
        }
    }

    // ========== batch-sync：FULL diff 三元组 + N+1 消解 + fail-closed ==========

    @Nested
    class BatchSync {

        private DependencyBatchSyncReq.DependencySyncItem item(String sourceCode, List<String> sourceOps,
                                                                List<String> requiredOps) {
            return new DependencyBatchSyncReq.DependencySyncItem(
                "MENU", sourceCode, null, sourceOps, "API", "api:hello", null, requiredOps, null, "d");
        }

        @Test
        void shouldDiffByTripleOnFullSync() {
            stubTypeLevelPermission(OperationCodeConstants.SYNC, true);
            ResourceResolveKey sourceKey = new ResourceResolveKey("MENU", "menu:sys", null, null);
            ResourceResolveKey targetKey = new ResourceResolveKey("API", "api:hello", null, null);
            when(typeResolutionService.batchResolveResourceIds(eq(TENANT), anyList()))
                .thenReturn(Map.of(sourceKey, SOURCE_ID, targetKey, TARGET_ID));
            // 操作位：源类型 MENU 的 VIEW=2；目标类型 API 的 ACCESS=4
            when(typeResolutionService.batchResolveOperationIds(eq(TENANT), eq("MENU"), anySet()))
                .thenReturn(Map.of("VIEW", 31L));
            when(typeResolutionService.batchResolveOperationIds(eq(TENANT), eq("API"), anySet()))
                .thenReturn(Map.of("ACCESS", 41L));
            when(operationPermissionMapper.selectValidByIds(eq(TENANT), anySet())).thenReturn(List.of(
                opOf(31L, 2L), opOf(41L, 4L)));

            // 存量三行（同 owner 范围）：7=三元组命中（保留+update）、8=同资源对不同触发位（应删）、
            // 9=资源对不在清单（应删）——旧实现按资源对匹配：8 漏删、且 upsert 按对定位会把 7 的位
            // 覆写到错行，本用例锁死三元组语义
            ResourceDependency row7 = newDep(7L, SOURCE_ID, TARGET_ID, 2L, 2L);
            ResourceDependency row8 = newDep(8L, SOURCE_ID, TARGET_ID, 4L, 2L);
            ResourceDependency row9 = newDep(9L, 33L, 44L, null, 2L);
            when(dependencyMapper.selectByOwnerService(TENANT, "example-service", "SERVICE_SYNC"))
                .thenReturn(List.of(row7, row8, row9));
            when(dependencyMapper.selectBySourceAndTargetIds(eq(TENANT), anySet(), anySet()))
                .thenReturn(List.of(row7, row8));

            service.batchSyncDependencies(TENANT, new DependencyBatchSyncReq("example-service", "SERVICE_SYNC",
                "FULL", List.of(item("menu:sys", List.of("VIEW"), List.of("ACCESS")))), OPERATOR);

            verify(dependencyMapper).softDeleteBatch(eq(TENANT), eq(List.of(8L, 9L)), any());
            verify(dependencyMapper).update(row7);
            verify(dependencyMapper, never()).insertBatch(anyList());
        }

        @Test
        void shouldResolveOperationBitsOncePerResourceType() {
            stubTypeLevelPermission(OperationCodeConstants.SYNC, true);
            ResourceResolveKey sourceKey1 = new ResourceResolveKey("MENU", "menu:sys", null, null);
            ResourceResolveKey sourceKey2 = new ResourceResolveKey("MENU", "menu:other", null, null);
            ResourceResolveKey targetKey = new ResourceResolveKey("API", "api:hello", null, null);
            when(typeResolutionService.batchResolveResourceIds(eq(TENANT), anyList()))
                .thenReturn(Map.of(sourceKey1, SOURCE_ID, sourceKey2, 33L, targetKey, TARGET_ID));
            // 两个条目同类型（MENU/API）：操作码按类型聚合后各解析一次（旧实现逐条目 ×2 轮）
            when(typeResolutionService.batchResolveOperationIds(eq(TENANT), eq("MENU"), anySet()))
                .thenReturn(Map.of("VIEW", 31L, "UPDATE", 32L));
            when(typeResolutionService.batchResolveOperationIds(eq(TENANT), eq("API"), anySet()))
                .thenReturn(Map.of("ACCESS", 41L));
            when(operationPermissionMapper.selectValidByIds(eq(TENANT), anySet()))
                .thenReturn(List.of(opOf(31L, 2L), opOf(32L, 4L), opOf(41L, 8L)));
            when(dependencyMapper.selectBySourceAndTargetIds(eq(TENANT), anySet(), anySet())).thenReturn(List.of());

            service.batchSyncDependencies(TENANT, new DependencyBatchSyncReq("example-service", "SERVICE_SYNC",
                null, List.of(
                    item("menu:sys", List.of("VIEW"), List.of("ACCESS")),
                    item("menu:other", List.of("VIEW", "UPDATE"), List.of("ACCESS")))), OPERATOR);

            verify(typeResolutionService, times(2)).batchResolveOperationIds(eq(TENANT), anyString(), anySet());
            verify(operationPermissionMapper, atMost(2)).selectValidByIds(eq(TENANT), anySet());
        }

        @Test
        void shouldThrow20005WhenSyncItemOperationCodeUnknown() {
            stubTypeLevelPermission(OperationCodeConstants.SYNC, true);
            ResourceResolveKey sourceKey = new ResourceResolveKey("MENU", "menu:sys", null, null);
            ResourceResolveKey targetKey = new ResourceResolveKey("API", "api:hello", null, null);
            when(typeResolutionService.batchResolveResourceIds(eq(TENANT), anyList()))
                .thenReturn(Map.of(sourceKey, SOURCE_ID, targetKey, TARGET_ID));
            when(typeResolutionService.batchResolveOperationIds(eq(TENANT), eq("API"), anySet()))
                .thenReturn(Map.of());

            assertThatThrownBy(() -> service.batchSyncDependencies(TENANT, new DependencyBatchSyncReq(
                "example-service", "SERVICE_SYNC", null,
                List.of(item("menu:sys", null, List.of("ACCES")))), OPERATOR))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(PermissionErrorCode.OPERATION_NOT_FOUND.getCode()));
            verify(dependencyMapper, never()).insertBatch(anyList());
        }

        @Test
        void shouldThrow20044WhenSyncItemMissingRequiredOperationCodes() {
            stubTypeLevelPermission(OperationCodeConstants.SYNC, true);
            ResourceResolveKey sourceKey = new ResourceResolveKey("MENU", "menu:sys", null, null);
            ResourceResolveKey targetKey = new ResourceResolveKey("API", "api:hello", null, null);
            when(typeResolutionService.batchResolveResourceIds(eq(TENANT), anyList()))
                .thenReturn(Map.of(sourceKey, SOURCE_ID, targetKey, TARGET_ID));

            // required_operation_bits 列 NOT NULL：缺失即畸形清单，显式 20044 而非 DB 约束 500
            assertThatThrownBy(() -> service.batchSyncDependencies(TENANT, new DependencyBatchSyncReq(
                "example-service", "SERVICE_SYNC", null,
                List.of(item("menu:sys", null, null))), OPERATOR))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(PermissionErrorCode.INVALID_PARAM.getCode()));
        }
    }

    private static OperationPermission opOf(Long id, Long bit) {
        OperationPermission op = new OperationPermission();
        op.setId(id);
        op.setBinaryBit(bit);
        return op;
    }

    // ========== DTO Bean Validation ==========

    @Nested
    class DtoBeanValidation {

        private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

        @Test
        void shouldValidateUpdateReqRequiredFields() {
            var violations = validator.validate(new ResourceDependencyUpdateReq(
                7L, " ", " ", null, null, " ", " ", null, List.<String>of(), null, "x".repeat(513)));
            assertThat(violations).hasSize(6);
            assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                .contains("sourceResourceTypeCode", "sourceResourceCode", "targetResourceTypeCode",
                    "targetResourceCode", "requiredOperationCodes", "description");
        }

        @Test
        void shouldRejectLegacyMaintainSourceEnum() {
            // DTO 注释旧枚举 SERVICE/MANUAL 系漂移：schema 四值白名单（T-PERM-031 收口）
            var violations = validator.validate(new DependencyBatchSyncReq(
                "example-service", "SERVICE", null, null));
            assertThat(violations).hasSize(1);

            assertThat(validator.validate(new DependencyBatchSyncReq(
                "example-service", "MANIFEST", null, null))).isEmpty();
        }

        @Test
        void shouldValidateCreateReqDescriptionLength() {
            var violations = validator.validate(new ResourceDependencyCreateReq(
                "MENU", "menu:sys", null, null, "API", "api:hello", null,
                List.of("ACCESS"), null, "x".repeat(513)));
            assertThat(violations).hasSize(1);
        }
    }
}
