package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.RequestContext;
import cn.ac.fage.accessmesh.access.permission.dto.req.TypeCreateReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.TypeUpdateReq;
import cn.ac.fage.accessmesh.access.permission.dto.resp.TypeDefinitionResp;
import cn.ac.fage.accessmesh.access.permission.entity.TypeDefinition;
import cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.access.permission.mapper.TypeDefinitionMapper;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 类型定义应用服务测试类
 * <p>
 * 测试TypeDefinitionAppServiceImpl的各项功能。
 * T-PERM-023 收口后：typeValue 服务端自动分配（软删不复用）、typeCode 可选生成+查重、
 * list 服务端过滤分页。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class TypeDefinitionAppServiceImplTest {

    @Mock private TypeDefinitionMapper typeDefinitionMapper;
    @Mock private cn.ac.fage.accessmesh.access.permission.mapper.OperationPermissionMapper operationPermissionMapper;
    @Mock private PermQueryEngine engine;
    @Mock private cn.ac.fage.accessmesh.access.permission.mapper.ServiceConfigMapper serviceConfigMapper;
    @Mock private cn.ac.fage.accessmesh.access.permission.service.domain.ResourceEntityDomainService resourceEntityDomainService;
    @Mock private cn.ac.fage.accessmesh.access.permission.service.domain.LocalProjectionDomainService localProjectionDomainService;
    @Mock private cn.ac.fage.accessmesh.access.permission.service.domain.SubjectDomainService subjectDomainService;
    @Mock private cn.ac.fage.accessmesh.access.permission.mapper.RoleResourcePermissionMapper rolePermMapper;
    @Mock private cn.ac.fage.accessmesh.access.permission.mapper.ResourceApiMappingMapper apiMappingMapper;
    @Mock private cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport treeWriteLockSupport;
    @Mock private cn.ac.fage.accessmesh.common.cache.CacheService cacheService;

    private TypeDefinitionAppServiceImpl service;

    @BeforeEach
    void setUp() {
        // T-PERM-052：真实守卫实例（mapper mock）——声明校验与变更守卫在 AppService 路径真实执行
        cn.ac.fage.accessmesh.access.permission.service.domain.ResourceTypeOwnershipGuard ownershipGuard =
            new cn.ac.fage.accessmesh.access.permission.service.domain.ResourceTypeOwnershipGuard(
                typeDefinitionMapper, serviceConfigMapper, resourceEntityDomainService,
                new com.fasterxml.jackson.databind.ObjectMapper());
        service = new TypeDefinitionAppServiceImpl(
            typeDefinitionMapper, operationPermissionMapper, engine, ownershipGuard,
            resourceEntityDomainService, localProjectionDomainService, subjectDomainService,
            rolePermMapper, org.mockito.Mockito.mock(cn.ac.fage.accessmesh.access.permission.service.domain.PermissionConditionDomainService.class), apiMappingMapper, treeWriteLockSupport, cacheService
        );
        // list/count 走 OperatorContext（读 AccessRequestContext），绑定用户上下文
        AccessRequestContext.bind(RequestContext.user(1L, 100L));
    }

    @AfterEach
    void tearDown() {
        AccessRequestContext.clear();
    }

    @Test
    void shouldCreateTypeWhenPermissionGranted() {
        when(engine.hasPermissionByCode(eq(1L), eq(100L), any(), eq((String) null), any()))
            .thenReturn(true);
        when(typeDefinitionMapper.selectMaxTypeValueAllRows(1L, "resource_type")).thenReturn(5);
        when(typeDefinitionMapper.selectByTypeKeyAndCode(1L, "resource_type", "CUSTOM")).thenReturn(null);

        TypeCreateReq req = new TypeCreateReq(
            "resource_type", "CUSTOM", "TestType", "A test type", 0, null
        );

        TypeDefinitionResp result = service.createType(1L, req, 100L);

        ArgumentCaptor<TypeDefinition> captor = ArgumentCaptor.forClass(TypeDefinition.class);
        verify(typeDefinitionMapper).insert(captor.capture());
        TypeDefinition inserted = captor.getValue();

        assertNotNull(result);
        assertEquals("TestType", result.name());
        assertEquals("resource_type", inserted.getTypeKey());
        assertEquals("CUSTOM", inserted.getTypeCode());
        assertEquals(100L, inserted.getCreatedBy());
    }

    @Test
    void shouldAllocateTypeValueFromMaxPlusOneIncludingDeletedRows() {
        // 回归锁：typeValue 由服务端分配，且 max 查询必须含软删行（软删不复用，T-PERM-019 D1）
        when(engine.hasPermissionByCode(anyLong(), anyLong(), any(), any(), any()))
            .thenReturn(true);
        when(typeDefinitionMapper.selectMaxTypeValueAllRows(1L, "resource_type")).thenReturn(7);

        TypeCreateReq req = new TypeCreateReq("resource_type", null, "AutoCode", null, null, null);

        service.createType(1L, req, 100L);

        ArgumentCaptor<TypeDefinition> captor = ArgumentCaptor.forClass(TypeDefinition.class);
        verify(typeDefinitionMapper).insert(captor.capture());
        assertEquals(8, captor.getValue().getTypeValue());
        // typeCode 留空 → <TYPEKEY大写>_<typeValue> 生成
        assertEquals("RESOURCE_TYPE_8", captor.getValue().getTypeCode());
        // isSystem 固定 false：系统预置仅走种子，不可由 API 创建
        assertEquals(false, captor.getValue().getIsSystem());
        // 生成路径不查重：显式码抢占未来生成码的场景由 DB uk_type_definition_code 兜底映射 20049（见 DIVE 用例）
        verify(typeDefinitionMapper, never()).selectByTypeKeyAndCode(anyLong(), any(), any());
    }

    @Test
    void shouldAllocateTypeValueFromOneWhenNoRows() {
        when(engine.hasPermissionByCode(anyLong(), anyLong(), any(), any(), any()))
            .thenReturn(true);
        when(typeDefinitionMapper.selectMaxTypeValueAllRows(1L, "group_type")).thenReturn(null);

        service.createType(1L, new TypeCreateReq("group_type", null, "First", null, null, null), 100L);

        ArgumentCaptor<TypeDefinition> captor = ArgumentCaptor.forClass(TypeDefinition.class);
        verify(typeDefinitionMapper).insert(captor.capture());
        assertEquals(1, captor.getValue().getTypeValue());
        assertEquals("GROUP_TYPE_1", captor.getValue().getTypeCode());
    }

    @Test
    void shouldRejectDuplicateExplicitTypeCode() {
        when(engine.hasPermissionByCode(anyLong(), anyLong(), any(), any(), any()))
            .thenReturn(true);
        when(typeDefinitionMapper.selectMaxTypeValueAllRows(1L, "resource_type")).thenReturn(5);
        when(typeDefinitionMapper.selectByTypeKeyAndCode(1L, "resource_type", "MENU"))
            .thenReturn(new TypeDefinition());

        TypeCreateReq req = new TypeCreateReq("resource_type", "MENU", "Dup", null, null, null);

        BizException exception = assertThrows(BizException.class,
            () -> service.createType(1L, req, 100L));

        assertEquals(PermissionErrorCode.TYPE_DEFINITION_CODE_DUPLICATE.getCode(), exception.getErrorCode());
        verify(typeDefinitionMapper, never()).insert(any(TypeDefinition.class));
    }

    @Test
    void shouldMapCodeUniqueViolationTo20049() {
        // 回归锁：显式码可抢占未来生成码（如先显式建 GROUP_TYPE_5，第 5 次留空创建生成同码）——
        // 生成路径不查重，DB uk_type_definition_code 兜底须映射 20049 而非裸 99999（重试永久失败场景）
        when(engine.hasPermissionByCode(anyLong(), anyLong(), any(), any(), any()))
            .thenReturn(true);
        when(typeDefinitionMapper.selectMaxTypeValueAllRows(1L, "group_type")).thenReturn(4);
        when(typeDefinitionMapper.insert(any(TypeDefinition.class)))
            .thenThrow(new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"uk_type_definition_code\""));

        TypeCreateReq req = new TypeCreateReq("group_type", null, "Auto", null, null, null);

        BizException exception = assertThrows(BizException.class, () -> service.createType(1L, req, 100L));
        assertEquals(PermissionErrorCode.TYPE_DEFINITION_CODE_DUPLICATE.getCode(), exception.getErrorCode());
    }

    @Test
    void shouldMapConcurrentValueViolationTo20049() {
        // 并发 max+1 撞值（两个并发 create 同 typeKey 同读 max）：uk_type_definition_value 兜底映射 20049，
        // 先提交方落库后重试即成功（瞬态，与码抢占的持久失败不同）
        when(engine.hasPermissionByCode(anyLong(), anyLong(), any(), any(), any()))
            .thenReturn(true);
        when(typeDefinitionMapper.selectMaxTypeValueAllRows(1L, "group_type")).thenReturn(4);
        when(typeDefinitionMapper.selectByTypeKeyAndCode(1L, "group_type", "EXPLICIT")).thenReturn(null);
        when(typeDefinitionMapper.insert(any(TypeDefinition.class)))
            .thenThrow(new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"uk_type_definition_value\""));

        TypeCreateReq req = new TypeCreateReq("group_type", "EXPLICIT", "Concurrent", null, null, null);

        BizException exception = assertThrows(BizException.class, () -> service.createType(1L, req, 100L));
        assertEquals(PermissionErrorCode.TYPE_DEFINITION_CODE_DUPLICATE.getCode(), exception.getErrorCode());
    }

    @Test
    void shouldRethrowNonUniqueViolationDive() {
        when(engine.hasPermissionByCode(anyLong(), anyLong(), any(), any(), any()))
            .thenReturn(true);
        when(typeDefinitionMapper.selectMaxTypeValueAllRows(1L, "group_type")).thenReturn(null);
        when(typeDefinitionMapper.insert(any(TypeDefinition.class)))
            .thenThrow(new DataIntegrityViolationException("some other constraint"));

        TypeCreateReq req = new TypeCreateReq("group_type", null, "First", null, null, null);

        assertThrows(DataIntegrityViolationException.class, () -> service.createType(1L, req, 100L));
    }

    @Test
    void shouldThrowWhenCreateTypePermissionDenied() {
        when(engine.hasPermissionByCode(eq(1L), eq(100L), any(), eq((String) null), any()))
            .thenReturn(false);

        TypeCreateReq req = new TypeCreateReq(
            "resource_type", null, "TestType", "A test type", 0, null
        );

        assertThrows(SecurityException.class, () -> service.createType(1L, req, 100L));
    }

    @Test
    void shouldPresetCrudOperationsWhenCreatingResourceType() {
        // T-PERM-028：resource_type 新类型联动预置 CRUD 四操作位（DDL 预置组模板同款）
        when(engine.hasPermissionByCode(anyLong(), anyLong(), any(), any(), any()))
            .thenReturn(true);
        when(typeDefinitionMapper.selectMaxTypeValueAllRows(1L, "resource_type")).thenReturn(9);

        service.createType(1L, new TypeCreateReq("resource_type", null, "NewRes", null, null, null), 100L);

        ArgumentCaptor<List<cn.ac.fage.accessmesh.access.permission.entity.OperationPermission>> opCaptor =
            ArgumentCaptor.forClass(List.class);
        verify(operationPermissionMapper).insertBatch(opCaptor.capture());
        List<cn.ac.fage.accessmesh.access.permission.entity.OperationPermission> preset = opCaptor.getValue();
        // 模板对齐 DDL 预置组：CREATE(1,0)/VIEW(2,0)/UPDATE(4,2)/DELETE(8,2)，resource_type=新 typeValue
        String[][] expected = {{"CREATE", "1", "0"}, {"VIEW", "2", "0"}, {"UPDATE", "4", "2"}, {"DELETE", "8", "2"}};
        for (int i = 0; i < 4; i++) {
            assertEquals(expected[i][0], preset.get(i).getCode(), "第 " + i + " 条操作码");
            assertEquals(Long.parseLong(expected[i][1]), preset.get(i).getBinaryBit());
            assertEquals(Long.parseLong(expected[i][2]), preset.get(i).getInheritMask());
            assertEquals(10, preset.get(i).getResourceType());
            assertEquals(1L, preset.get(i).getTenantId());
        }
    }

    @Test
    void shouldNotPresetOperationsForNonResourceTypeKey() {
        when(engine.hasPermissionByCode(anyLong(), anyLong(), any(), any(), any()))
            .thenReturn(true);
        when(typeDefinitionMapper.selectMaxTypeValueAllRows(1L, "group_type")).thenReturn(null);

        service.createType(1L, new TypeCreateReq("group_type", null, "First", null, null, null), 100L);

        verify(operationPermissionMapper, never()).insertBatch(any());
    }

    @Test
    void shouldListTypesWithNormalizedFilters() {
        when(engine.hasPermissionByCode(eq(1L), eq(100L), any(), eq((String) null), any()))
            .thenReturn(true);
        TypeDefinition row = new TypeDefinition();
        row.setId(9L);
        row.setTenantId(1L);
        row.setTypeKey("resource_type");
        row.setTypeCode("MENU");
        row.setTypeValue(1);
        row.setName("菜单");
        row.setSortOrder(2);
        when(typeDefinitionMapper.selectPageByCondition(eq(1L), eq("resource_type"), eq("men"), eq(10), eq(0)))
            .thenReturn(List.of(row));

        List<TypeDefinitionResp> result =
            service.listTypes(1L, " resource_type ", " men ", 0, 10);

        // 空白规整为 null 的语义：显式传值走 trim 后过滤
        assertEquals(1, result.size());
        assertEquals("MENU", result.get(0).typeCode());
        verify(typeDefinitionMapper).selectPageByCondition(eq(1L), eq("resource_type"), eq("men"), eq(10), eq(0));
    }

    @Test
    void shouldNormalizeBlankFiltersToNull() {
        when(engine.hasPermissionByCode(anyLong(), anyLong(), any(), any(), any()))
            .thenReturn(true);
        when(typeDefinitionMapper.selectPageByCondition(eq(1L), isNull(), isNull(), anyInt(), anyInt()))
            .thenReturn(List.of());

        service.listTypes(1L, "  ", "", 0, 200);

        verify(typeDefinitionMapper).selectPageByCondition(eq(1L), isNull(), isNull(), eq(200), eq(0));
    }

    @Test
    void shouldCountTypesWithPermission() {
        when(engine.hasPermissionByCode(anyLong(), anyLong(), any(), any(), any()))
            .thenReturn(true);
        when(typeDefinitionMapper.countByCondition(1L, null, null)).thenReturn(42L);

        assertEquals(42L, service.countTypes(1L, null, null));
    }

    @Test
    void shouldThrowTypeDefinitionNotFoundWhenUpdateTargetMissing() {
        when(engine.hasPermissionByCode(eq(1L), eq(100L), any(), any(), any()))
            .thenReturn(true);
        when(typeDefinitionMapper.selectValidById(1L, 99L)).thenReturn(null);

        TypeUpdateReq req = new TypeUpdateReq(99L, "Updated", "desc", 1, null);

        BizException exception = assertThrows(BizException.class, () -> service.updateType(1L, req, 100L));

        assertEquals(PermissionErrorCode.TYPE_DEFINITION_NOT_FOUND.getCode(), exception.getErrorCode());
    }

    @Test
    void shouldListTypesWhenOnlyInstanceLevelViewGranted() {
        // 2026-09-03 门禁放宽：类型级拒绝但任一实例级 VIEW 命中即可查询（与登录权限串口径对齐；
        // 旧实现仅认类型级，此用例必红）。T-PERM-051：实例键为复合键 {typeKey}:{typeCode}
        // （旧实现按裸 typeCode 判定，此用例组在旧实现下失败）
        when(engine.hasPermissionByCode(eq(1L), eq(100L), any(), eq((String) null), any()))
            .thenReturn(false);
        when(typeDefinitionMapper.selectValidByTenant(1L))
            .thenReturn(List.of(typeRow("user_type", "USER_TYPE"), typeRow("role_type", "ROLE_TYPE"),
                typeRow("resource_type", "RESOURCE_TYPE")));
        when(engine.getDeniedResourceCodes(eq(1L), eq(100L), any(), any(), any()))
            .thenReturn(java.util.Set.of("user_type:USER_TYPE", "role_type:ROLE_TYPE"));
        when(typeDefinitionMapper.selectPageByCondition(eq(1L), isNull(), isNull(), anyInt(), anyInt()))
            .thenReturn(List.of());

        assertEquals(List.of(), service.listTypes(1L, null, null, 0, 200));
        verify(typeDefinitionMapper).selectPageByCondition(eq(1L), isNull(), isNull(), eq(200), eq(0));
    }

    @Test
    void shouldThrowWhenAllInstancesDeniedAndTypeLevelDenied() {
        when(engine.hasPermissionByCode(eq(1L), eq(100L), any(), eq((String) null), any()))
            .thenReturn(false);
        when(typeDefinitionMapper.selectValidByTenant(1L))
            .thenReturn(List.of(typeRow("user_type", "USER_TYPE"), typeRow("role_type", "ROLE_TYPE")));
        when(engine.getDeniedResourceCodes(eq(1L), eq(100L), any(), any(), any()))
            .thenReturn(java.util.Set.of("user_type:USER_TYPE", "role_type:ROLE_TYPE"));

        assertThrows(SecurityException.class, () -> service.listTypes(1L, null, null, 0, 200));
        verify(typeDefinitionMapper, never()).selectPageByCondition(anyLong(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void shouldPassCompositeKeysToInstanceGate() {
        // T-PERM-051 回归锁：list 实例门禁的引擎入参必须是复合键集合（裸 typeCode 在跨 type_key
        // 重码下无法唯一命中投影行——user_type 与 resource_type 均有 USER/SERVICE 同名行）
        when(engine.hasPermissionByCode(eq(1L), eq(100L), any(), eq((String) null), any()))
            .thenReturn(false);
        when(typeDefinitionMapper.selectValidByTenant(1L))
            .thenReturn(List.of(typeRow("user_type", "USER"), typeRow("resource_type", "USER"),
                typeRow("resource_type", "HR_ORG")));
        when(engine.getDeniedResourceCodes(eq(1L), eq(100L), any(), any(), any()))
            .thenReturn(java.util.Set.of("user_type:USER", "resource_type:USER"));
        when(typeDefinitionMapper.selectPageByCondition(eq(1L), isNull(), isNull(), anyInt(), anyInt()))
            .thenReturn(List.of());

        service.listTypes(1L, null, null, 0, 200);

        // 复合键集合：跨 type_key 同码 USER 以 typeKey 区分，均为三段式复合键
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<java.util.Set<String>> captor =
            (org.mockito.ArgumentCaptor<java.util.Set<String>>) (org.mockito.ArgumentCaptor<?>)
                org.mockito.ArgumentCaptor.forClass(java.util.Set.class);
        org.mockito.Mockito.verify(engine).getDeniedResourceCodes(eq(1L), eq(100L), any(),
            captor.capture(), any());
        assertEquals(java.util.Set.of("user_type:USER", "resource_type:USER", "resource_type:HR_ORG"),
            captor.getValue());
    }

    @Test
    void shouldThrowWhenNoTypeInstancesExistAndTypeLevelDenied() {
        // 无实例可判定时 fail-closed（空清单无法证明任何实例级授权）
        when(engine.hasPermissionByCode(eq(1L), eq(100L), any(), eq((String) null), any()))
            .thenReturn(false);
        when(typeDefinitionMapper.selectValidByTenant(1L))
            .thenReturn(List.of());

        assertThrows(SecurityException.class, () -> service.countTypes(1L, null, null));
    }

    /** list 实例门禁复合键构造用最小类型行 */
    private static TypeDefinition typeRow(String typeKey, String typeCode) {
        TypeDefinition row = new TypeDefinition();
        row.setTenantId(1L);
        row.setTypeKey(typeKey);
        row.setTypeCode(typeCode);
        return row;
    }

    // ========== T-PERM-052：extra 所有权声明（managedMode/syncSourceService）==========
    // 以下声明校验/变更守卫用例在旧实现（不校验 extra、无变更守卫）下会因落库成功而失败。

    private static final String SYNC_DECLARATION =
        "{\"managedMode\":\"SYNC\",\"syncSourceService\":\"hr-service\"}";

    /** 已注册且启用（status=1、未软删）的 hr-service 注册行——保存侧与运行时同规则的合法来源 */
    private static cn.ac.fage.accessmesh.access.permission.entity.ServiceConfig registeredEnabledService() {
        cn.ac.fage.accessmesh.access.permission.entity.ServiceConfig config =
            new cn.ac.fage.accessmesh.access.permission.entity.ServiceConfig();
        config.setStatus(1);
        config.setDeleteFlag(0L);
        return config;
    }

    @Test
    void shouldCreateSyncDeclaredResourceType_whenSourceServiceRegistered() {
        when(engine.hasPermissionByCode(anyLong(), anyLong(), any(), any(), any())).thenReturn(true);
        when(typeDefinitionMapper.selectMaxTypeValueAllRows(1L, "resource_type")).thenReturn(5);
        when(serviceConfigMapper.selectByTenantAndServiceCode(1L, "hr-service"))
            .thenReturn(registeredEnabledService());

        service.createType(1L, new TypeCreateReq("resource_type", "HR_ORG", "HR组织", null, null,
            SYNC_DECLARATION), 100L);

        ArgumentCaptor<TypeDefinition> captor = ArgumentCaptor.forClass(TypeDefinition.class);
        verify(typeDefinitionMapper).insert(captor.capture());
        assertEquals(SYNC_DECLARATION, captor.getValue().getExtra());
    }

    @Test
    void shouldRejectDeclarationOnNonResourceTypeKey() {
        when(engine.hasPermissionByCode(anyLong(), anyLong(), any(), any(), any())).thenReturn(true);

        BizException ex = assertThrows(BizException.class, () -> service.createType(1L,
            new TypeCreateReq("group_type", "G1", "组", null, null, SYNC_DECLARATION), 100L));
        assertEquals(PermissionErrorCode.INVALID_PARAM.getCode(), ex.getErrorCode());
        verify(typeDefinitionMapper, never()).insert(any(TypeDefinition.class));
    }

    @Test
    void shouldRejectSyncModeWithoutSourceService() {
        when(engine.hasPermissionByCode(anyLong(), anyLong(), any(), any(), any())).thenReturn(true);

        BizException ex = assertThrows(BizException.class, () -> service.createType(1L,
            new TypeCreateReq("resource_type", "HR_ORG", "HR组织", null, null,
                "{\"managedMode\":\"SYNC\"}"), 100L));
        assertEquals(PermissionErrorCode.INVALID_PARAM.getCode(), ex.getErrorCode());
        verify(typeDefinitionMapper, never()).insert(any(TypeDefinition.class));
    }

    @Test
    void shouldRejectInvalidManagedModeValue() {
        when(engine.hasPermissionByCode(anyLong(), anyLong(), any(), any(), any())).thenReturn(true);

        BizException ex = assertThrows(BizException.class, () -> service.createType(1L,
            new TypeCreateReq("resource_type", "HR_ORG", "HR组织", null, null,
                "{\"managedMode\":\"AUTO\"}"), 100L));
        assertEquals(PermissionErrorCode.INVALID_PARAM.getCode(), ex.getErrorCode());
    }

    @Test
    void shouldRejectSyncModeWithUnregisteredSourceService() {
        when(engine.hasPermissionByCode(anyLong(), anyLong(), any(), any(), any())).thenReturn(true);
        when(serviceConfigMapper.selectByTenantAndServiceCode(1L, "hr-service")).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> service.createType(1L,
            new TypeCreateReq("resource_type", "HR_ORG", "HR组织", null, null,
                SYNC_DECLARATION), 100L));
        assertEquals(PermissionErrorCode.INVALID_PARAM.getCode(), ex.getErrorCode());
        verify(typeDefinitionMapper, never()).insert(any(TypeDefinition.class));
    }

    @Test
    void shouldRejectDeclarationChangeWhenTypeHasValidRows() {
        // 无有效行才可改（2026-09-05 定案）：类型下有行时 managedMode 变更 → 20056；
        // 旧实现无守卫会直接落库
        when(engine.hasPermissionByCode(eq(1L), eq(100L), any(), any(), any())).thenReturn(true);
        TypeDefinition existing = new TypeDefinition();
        existing.setId(9L);
        existing.setTenantId(1L);
        existing.setTypeKey("resource_type");
        existing.setTypeCode("HR_ORG");
        existing.setTypeValue(5);
        existing.setExtra(SYNC_DECLARATION);
        when(typeDefinitionMapper.selectValidById(1L, 9L)).thenReturn(existing);
        when(resourceEntityDomainService.hasValidRowsOfType(1L, 5)).thenReturn(true);

        BizException ex = assertThrows(BizException.class, () -> service.updateType(1L,
            new TypeUpdateReq(9L, null, null, null, "{\"managedMode\":\"MANAGED\"}"), 100L));
        assertEquals(PermissionErrorCode.TYPE_OWNERSHIP_CHANGE_CONFLICT.getCode(), ex.getErrorCode());
        verify(typeDefinitionMapper, never()).update(any(TypeDefinition.class));
    }

    @Test
    void shouldRejectImplicitModeRevertWhenTypeHasValidRows() {
        // 删键=隐式切回 MANAGED（extra 整串替换）：同样视为有效值变更 → 20056
        when(engine.hasPermissionByCode(eq(1L), eq(100L), any(), any(), any())).thenReturn(true);
        TypeDefinition existing = new TypeDefinition();
        existing.setId(9L);
        existing.setTenantId(1L);
        existing.setTypeKey("resource_type");
        existing.setTypeCode("HR_ORG");
        existing.setTypeValue(5);
        existing.setExtra(SYNC_DECLARATION);
        when(typeDefinitionMapper.selectValidById(1L, 9L)).thenReturn(existing);
        when(resourceEntityDomainService.hasValidRowsOfType(1L, 5)).thenReturn(true);

        BizException ex = assertThrows(BizException.class, () -> service.updateType(1L,
            new TypeUpdateReq(9L, "改名", null, null, "{\"k\":1}"), 100L));
        assertEquals(PermissionErrorCode.TYPE_OWNERSHIP_CHANGE_CONFLICT.getCode(), ex.getErrorCode());
    }

    @Test
    void shouldAllowDeclarationChangeWhenTypeHasNoValidRows() {
        when(engine.hasPermissionByCode(eq(1L), eq(100L), any(), any(), any())).thenReturn(true);
        TypeDefinition existing = new TypeDefinition();
        existing.setId(9L);
        existing.setTenantId(1L);
        existing.setTypeKey("resource_type");
        existing.setTypeCode("HR_ORG");
        existing.setTypeValue(5);
        when(typeDefinitionMapper.selectValidById(1L, 9L)).thenReturn(existing);
        when(serviceConfigMapper.selectByTenantAndServiceCode(1L, "hr-service"))
            .thenReturn(registeredEnabledService());
        when(resourceEntityDomainService.hasValidRowsOfType(1L, 5)).thenReturn(false);

        service.updateType(1L, new TypeUpdateReq(9L, null, null, null, SYNC_DECLARATION), 100L);

        verify(typeDefinitionMapper).update(any(TypeDefinition.class));
    }

    @Test
    void shouldPinSystemTypeDeclarationButAllowUnchangedResubmissionAndFieldEdits() {
        // codex 三轮复评 P2-2：钉死只拒「有效声明变更」——同声明重复提交与仅改非声明字段必须放行
        // （isSystem 判定若误移到相等比较之前，系统类型正常编辑被 20056 阻断而本组拒绝用例仍绿）
        when(engine.hasPermissionByCode(eq(1L), eq(100L), any(), any(), any())).thenReturn(true);
        TypeDefinition user = new TypeDefinition();
        user.setId(9L);
        user.setTenantId(1L);
        user.setTypeKey("resource_type");
        user.setTypeCode("USER");
        user.setTypeValue(6);
        user.setIsSystem(true);
        user.setExtra("{\"managedMode\":\"SYNC\",\"syncSourceService\":\"access-service\"}");
        when(typeDefinitionMapper.selectValidById(1L, 9L)).thenReturn(user);

        // 同声明重复提交（仅附加无关键）+ 仅改名称（extra=null 维持原声明）
        service.updateType(1L, new TypeUpdateReq(9L, null, null, null,
            "{\"managedMode\":\"SYNC\",\"syncSourceService\":\"access-service\",\"k\":1}"), 100L);
        service.updateType(1L, new TypeUpdateReq(9L, "用户类型改名", null, null, null), 100L);

        verify(typeDefinitionMapper, org.mockito.Mockito.times(2)).update(any(TypeDefinition.class));
        // 声明未变：不触发行数查询
        verify(resourceEntityDomainService, never()).hasValidRowsOfType(anyLong(), any());
    }

    @Test
    void shouldLockTreeWritesForResourceTypeCreate() {
        // codex 三轮复评 P1-1：resource_type 类型创建与资源写入口共持树写锁（锁先于首次类型读取；
        // 管理面门禁对「类型不存在」放行，创建类型不持锁时在途资源插入可落进并发新建的 SYNC 类型）；
        // 非 resource_type 类型创建不持锁
        when(engine.hasPermissionByCode(anyLong(), anyLong(), any(), any(), any())).thenReturn(true);
        when(typeDefinitionMapper.selectMaxTypeValueAllRows(1L, "resource_type")).thenReturn(5);

        service.createType(1L, new TypeCreateReq("resource_type", "HR_ORG", "HR组织", null, null, null), 100L);

        // codex 四轮复评 P2-2：engine 入序（钉「权限→锁→首次类型读取」完整顺序）
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(engine, treeWriteLockSupport, typeDefinitionMapper);
        order.verify(engine).hasPermissionByCode(anyLong(), anyLong(), any(), any(), any());
        order.verify(treeWriteLockSupport).lockTreeWrites(1L,
            cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport.TreeLockTarget.RESOURCE_ENTITY);
        order.verify(typeDefinitionMapper).selectMaxTypeValueAllRows(1L, "resource_type");

        when(typeDefinitionMapper.selectMaxTypeValueAllRows(1L, "role_type")).thenReturn(2);
        org.mockito.Mockito.clearInvocations(treeWriteLockSupport);
        service.createType(1L, new TypeCreateReq("role_type", "TEAM_ROLE", "团队角色类型", null, null, null), 100L);
        verify(treeWriteLockSupport, never()).lockTreeWrites(anyLong(), any());
    }

    @Test
    void shouldEvictTypeResolutionCachesAfterCreate() {
        // codex 三轮复评 P1-2：新建类型提交后失效双向解析缓存键——删建同码不同值时旧映射不得残留
        when(engine.hasPermissionByCode(anyLong(), anyLong(), any(), any(), any())).thenReturn(true);
        when(typeDefinitionMapper.selectMaxTypeValueAllRows(1L, "role_type")).thenReturn(2);

        service.createType(1L, new TypeCreateReq("role_type", "TEAM_ROLE", "团队角色类型", null, null, null), 100L);

        verify(cacheService).evictAfterCommit(
            cn.ac.fage.accessmesh.access.permission.cache.PermCacheCatalog.TYPE_VALUE, 1L, "role_type:TEAM_ROLE");
        verify(cacheService).evictAfterCommit(
            cn.ac.fage.accessmesh.access.permission.cache.PermCacheCatalog.TYPE_CODE, 1L, "role_type:3");
    }

    @Test
    void shouldRejectDeclarationChangeForSystemTypeEvenWithoutRows() {
        // codex 二轮复评 P1-1 定案：系统预置类型所有权声明钉死——空 USER 类型翻成 MANAGED 后
        // 事实链路照旧投影写入即双 writer（顺序性破坏）；旧实现零行时放行
        when(engine.hasPermissionByCode(eq(1L), eq(100L), any(), any(), any())).thenReturn(true);
        TypeDefinition user = new TypeDefinition();
        user.setId(9L);
        user.setTenantId(1L);
        user.setTypeKey("resource_type");
        user.setTypeCode("USER");
        user.setTypeValue(6);
        user.setIsSystem(true);
        user.setExtra("{\"managedMode\":\"SYNC\",\"syncSourceService\":\"access-service\"}");
        when(typeDefinitionMapper.selectValidById(1L, 9L)).thenReturn(user);

        BizException ex = assertThrows(BizException.class, () -> service.updateType(1L,
            new TypeUpdateReq(9L, null, null, null, "{\"managedMode\":\"MANAGED\"}"), 100L));
        assertEquals(PermissionErrorCode.TYPE_OWNERSHIP_CHANGE_CONFLICT.getCode(), ex.getErrorCode());
        // 钉死判定先于行数查询
        verify(resourceEntityDomainService, never()).hasValidRowsOfType(anyLong(), any());
        verify(typeDefinitionMapper, never()).update(any(TypeDefinition.class));
    }

    @Test
    void shouldLockTreeWritesAndReReadForResourceTypeUpdate() {
        // codex 复评 P1 回归锁：resource_type 更新须持 (resource_entity, 租户) 树写锁并锁内重读
        // （与资源写入口互斥）；旧实现无锁且只读一次
        when(engine.hasPermissionByCode(eq(1L), eq(100L), any(), any(), any())).thenReturn(true);
        TypeDefinition hrOrg = new TypeDefinition();
        hrOrg.setId(9L);
        hrOrg.setTenantId(1L);
        hrOrg.setTypeKey("resource_type");
        hrOrg.setTypeCode("HR_ORG");
        hrOrg.setTypeValue(5);
        hrOrg.setIsSystem(false);
        when(typeDefinitionMapper.selectValidById(1L, 9L)).thenReturn(hrOrg);

        service.updateType(1L, new TypeUpdateReq(9L, "改名", null, null, null), 100L);

        verify(treeWriteLockSupport).lockTreeWrites(1L,
            cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport.TreeLockTarget.RESOURCE_ENTITY);
        // peek + 锁内重读共两次；InOrder 证明锁后仍有读取（重读在锁内）
        verify(typeDefinitionMapper, times(2)).selectValidById(1L, 9L);
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(treeWriteLockSupport, typeDefinitionMapper);
        order.verify(treeWriteLockSupport).lockTreeWrites(1L,
            cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport.TreeLockTarget.RESOURCE_ENTITY);
        order.verify(typeDefinitionMapper).selectValidById(1L, 9L);
        verify(typeDefinitionMapper).update(any(TypeDefinition.class));
    }

    @Test
    void shouldNotLockTreeWritesForNonResourceTypeUpdate() {
        when(engine.hasPermissionByCode(eq(1L), eq(100L), any(), any(), any())).thenReturn(true);
        TypeDefinition groupType = new TypeDefinition();
        groupType.setId(9L);
        groupType.setTenantId(1L);
        groupType.setTypeKey("group_type");
        groupType.setTypeCode("G1");
        groupType.setTypeValue(1);
        when(typeDefinitionMapper.selectValidById(1L, 9L)).thenReturn(groupType);

        service.updateType(1L, new TypeUpdateReq(9L, "改名", null, null, null), 100L);

        verify(treeWriteLockSupport, never()).lockTreeWrites(anyLong(),
            org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRejectTypeDeletionWhenTypeHasValidRows() {
        // 评审批次（2026-09-05）：类型下存在有效资源行时不可删除（20056，与声明变更守卫同款；
        // 旧实现无守卫会直接软删类型，其行成外部源与管理面都无法触达的永久孤儿）
        when(engine.getDeniedResourceCodes(anyLong(), anyLong(), any(), any(), any()))
            .thenReturn(java.util.Set.of());
        TypeDefinition hrOrg = new TypeDefinition();
        hrOrg.setId(9L);
        hrOrg.setTenantId(1L);
        hrOrg.setTypeKey("resource_type");
        hrOrg.setTypeCode("HR_ORG");
        hrOrg.setTypeValue(5);
        hrOrg.setIsSystem(false);
        when(typeDefinitionMapper.selectValidByIds(1L, java.util.Set.of(9L))).thenReturn(java.util.List.of(hrOrg));
        when(resourceEntityDomainService.findTypesWithValidRows(1L, java.util.Set.of(5)))
            .thenReturn(java.util.Set.of(5));

        BizException ex = assertThrows(BizException.class,
            () -> service.deleteTypesByIds(1L, java.util.List.of(9L), 100L));
        assertEquals(PermissionErrorCode.TYPE_OWNERSHIP_CHANGE_CONFLICT.getCode(), ex.getErrorCode());
        verify(typeDefinitionMapper, never()).softDeleteBatch(anyLong(), any(), any());
        // 批删 resource_type 同样持锁（codex P1）；codex 二轮复评 P2-2 回归锁：键构造读 →
        // 锁 → 锁内重读 → 行数守卫的完整顺序（删掉锁内重读/锁后置的旧实现下失败）
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(
            treeWriteLockSupport, typeDefinitionMapper, resourceEntityDomainService);
        order.verify(typeDefinitionMapper).selectValidByIds(1L, java.util.Set.of(9L));
        order.verify(treeWriteLockSupport).lockTreeWrites(1L,
            cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport.TreeLockTarget.RESOURCE_ENTITY);
        order.verify(typeDefinitionMapper).selectValidByIds(1L, java.util.Set.of(9L));
        order.verify(resourceEntityDomainService).findTypesWithValidRows(1L, java.util.Set.of(5));
    }

    @Test
    void shouldDeleteTypeWhenNoValidRows() {
        when(engine.getDeniedResourceCodes(anyLong(), anyLong(), any(), any(), any()))
            .thenReturn(java.util.Set.of());
        TypeDefinition hrOrg = new TypeDefinition();
        hrOrg.setId(9L);
        hrOrg.setTenantId(1L);
        hrOrg.setTypeKey("resource_type");
        hrOrg.setTypeCode("HR_ORG");
        hrOrg.setTypeValue(5);
        hrOrg.setIsSystem(false);
        when(typeDefinitionMapper.selectValidByIds(1L, java.util.Set.of(9L))).thenReturn(java.util.List.of(hrOrg));
        when(resourceEntityDomainService.findTypesWithValidRows(1L, java.util.Set.of(5)))
            .thenReturn(java.util.Set.of());

        service.deleteTypesByIds(1L, java.util.List.of(9L), 100L);

        verify(typeDefinitionMapper).softDeleteBatch(eq(1L), any(), any());
        // codex 三轮复评 P1-2：被删类型提交后失效双向解析缓存键；codex 四轮复评 P2：
        // 码键/值键各合并一次批量失效（逐项 evictAfterCommit = 2N 个事务回调）
        verify(cacheService).evictBatchAfterCommit(
            cn.ac.fage.accessmesh.access.permission.cache.PermCacheCatalog.TYPE_VALUE, 1L,
            java.util.Set.of("resource_type:HR_ORG"));
        verify(cacheService).evictBatchAfterCommit(
            cn.ac.fage.accessmesh.access.permission.cache.PermCacheCatalog.TYPE_CODE, 1L,
            java.util.Set.of("resource_type:5"));
    }

    @Test
    void shouldAllowUnrelatedExtraUpdateWithoutModeChange() {
        // 声明有效值未变（SYNC→SYNC 同来源）时，其余 extra 字段更新不受变更守卫限制
        when(engine.hasPermissionByCode(eq(1L), eq(100L), any(), any(), any())).thenReturn(true);
        TypeDefinition existing = new TypeDefinition();
        existing.setId(9L);
        existing.setTenantId(1L);
        existing.setTypeKey("resource_type");
        existing.setTypeCode("HR_ORG");
        existing.setTypeValue(5);
        existing.setExtra(SYNC_DECLARATION);
        when(typeDefinitionMapper.selectValidById(1L, 9L)).thenReturn(existing);
        when(serviceConfigMapper.selectByTenantAndServiceCode(1L, "hr-service"))
            .thenReturn(registeredEnabledService());

        service.updateType(1L, new TypeUpdateReq(9L, null, null, null,
            SYNC_DECLARATION.substring(0, SYNC_DECLARATION.length() - 1) + ",\"k\":1}"), 100L);

        verify(typeDefinitionMapper).update(any(TypeDefinition.class));
        // 声明未变不触发行数查询
        verify(resourceEntityDomainService, never()).hasValidRowsOfType(anyLong(), any());
    }

    // ========== T-PERM-051：TYPE_DEFINITION 实例投影联动 + 复合业务键门禁迁移 ==========

    @Test
    void shouldProjectTypeDefinitionOnCreate() {
        // 创建类型同事务维护 TYPE_DEFINITION 投影（复合业务键）；旧实现无投影联动，本用例必红
        when(engine.hasPermissionByCode(anyLong(), anyLong(), any(), any(), any())).thenReturn(true);
        when(typeDefinitionMapper.selectMaxTypeValueAllRows(1L, "group_type")).thenReturn(null);

        service.createType(1L, new TypeCreateReq("group_type", null, "First", null, null, null), 100L);

        verify(localProjectionDomainService).upsertTypeDefinitionResource(
            1L, "group_type", "GROUP_TYPE_1", "First");
    }

    @Test
    void shouldSyncProjectionNameOnlyWhenNameProvided() {
        when(engine.hasPermissionByCode(eq(1L), eq(100L), any(), any(), any())).thenReturn(true);
        TypeDefinition existing = new TypeDefinition();
        existing.setId(9L);
        existing.setTenantId(1L);
        existing.setTypeKey("resource_type");
        existing.setTypeCode("HR_ORG");
        existing.setTypeValue(5);
        when(typeDefinitionMapper.selectValidById(1L, 9L)).thenReturn(existing);

        service.updateType(1L, new TypeUpdateReq(9L, "改名", null, null, null), 100L);
        verify(localProjectionDomainService).upsertTypeDefinitionResource(
            1L, "resource_type", "HR_ORG", "改名");

        // name 未提供（仅改 sortOrder/description）不触发投影写
        org.mockito.Mockito.clearInvocations(localProjectionDomainService);
        service.updateType(1L, new TypeUpdateReq(9L, null, "desc", 3, null), 100L);
        verify(localProjectionDomainService, never()).upsertTypeDefinitionResource(anyLong(), any(), any(), any());
    }

    @Test
    void shouldGateUpdateByCompositeBusinessKey() {
        // T-PERM-051 回归锁：update 门禁按复合键 {typeKey}:{typeCode} 判定——
        // 旧实现传 String.valueOf(typeId)="9"（ID 空间错位），本用例在旧实现下必红
        when(engine.hasPermissionByCode(eq(1L), eq(100L), any(), eq("resource_type:HR_ORG"), any()))
            .thenReturn(true);
        TypeDefinition existing = new TypeDefinition();
        existing.setId(9L);
        existing.setTenantId(1L);
        existing.setTypeKey("resource_type");
        existing.setTypeCode("HR_ORG");
        existing.setTypeValue(5);
        when(typeDefinitionMapper.selectValidById(1L, 9L)).thenReturn(existing);

        service.updateType(1L, new TypeUpdateReq(9L, "改名", null, null, null), 100L);

        verify(typeDefinitionMapper).update(any(TypeDefinition.class));
    }

    @Test
    void shouldGateDetailByCompositeKeyAndFallBackToTypeLevelWhenRowMissing() {
        // 行存在：实例级复合键 VIEW 命中即可查详情（旧实现按 id 串判，本用例必红）
        TypeDefinition existing = new TypeDefinition();
        existing.setId(9L);
        existing.setTenantId(1L);
        existing.setTypeKey("resource_type");
        existing.setTypeCode("HR_ORG");
        existing.setTypeValue(5);
        existing.setName("HR组织");
        when(typeDefinitionMapper.selectValidById(1L, 9L)).thenReturn(existing);
        when(engine.hasPermissionByCode(eq(1L), eq(100L), any(), eq("resource_type:HR_ORG"), any()))
            .thenReturn(true);
        assertNotNull(service.getType(1L, 9L));

        // 行缺失：退化为类型级校验——无权限 SecurityException（保持既有可观察行为）、有权限 null
        when(typeDefinitionMapper.selectValidById(1L, 99L)).thenReturn(null);
        when(engine.hasPermissionByCode(eq(1L), eq(100L), any(), eq((String) null), any()))
            .thenReturn(false);
        assertThrows(SecurityException.class, () -> service.getType(1L, 99L));
        when(engine.hasPermissionByCode(eq(1L), eq(100L), any(), eq((String) null), any()))
            .thenReturn(true);
        assertNull(service.getType(1L, 99L));
    }

    @Test
    void shouldGateBatchDeleteByCompositeKeys() {
        // T-PERM-051 回归锁：批删门禁走编码轨复合键（旧实现 type_definition.id 直传实体轨，
        // ID 空间错位）——本用例在旧实现下因 getDeniedEntityIds 未被 stub 而必红
        when(engine.getDeniedResourceCodes(anyLong(), anyLong(), any(), any(), any()))
            .thenReturn(java.util.Set.of("resource_type:HR_ORG"));
        TypeDefinition hrOrg = new TypeDefinition();
        hrOrg.setId(9L);
        hrOrg.setTenantId(1L);
        hrOrg.setTypeKey("resource_type");
        hrOrg.setTypeCode("HR_ORG");
        hrOrg.setTypeValue(5);
        hrOrg.setIsSystem(false);
        when(typeDefinitionMapper.selectValidByIds(1L, java.util.Set.of(9L))).thenReturn(java.util.List.of(hrOrg));

        assertThrows(SecurityException.class,
            () -> service.deleteTypesByIds(1L, java.util.List.of(9L), 100L));
        verify(typeDefinitionMapper, never()).softDeleteBatch(anyLong(), any(), any());

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<java.util.Set<String>> captor =
            (org.mockito.ArgumentCaptor<java.util.Set<String>>) (org.mockito.ArgumentCaptor<?>)
                org.mockito.ArgumentCaptor.forClass(java.util.Set.class);
        verify(engine).getDeniedResourceCodes(eq(1L), eq(100L), any(), captor.capture(), any());
        assertEquals(java.util.Set.of("resource_type:HR_ORG"), captor.getValue());
    }

    @Test
    void shouldCascadeProjectionAndGrantRowsOnDelete() {
        // 2026-09-07 用户定案级联：类型软删同事务级联投影行 + 投影行下授权行（deleteResources 同款）
        when(engine.getDeniedResourceCodes(anyLong(), anyLong(), any(), any(), any()))
            .thenReturn(java.util.Set.of());
        TypeDefinition hrOrg = new TypeDefinition();
        hrOrg.setId(9L);
        hrOrg.setTenantId(1L);
        hrOrg.setTypeKey("resource_type");
        hrOrg.setTypeCode("HR_ORG");
        hrOrg.setTypeValue(5);
        hrOrg.setIsSystem(false);
        when(typeDefinitionMapper.selectValidByIds(1L, java.util.Set.of(9L))).thenReturn(java.util.List.of(hrOrg));
        when(resourceEntityDomainService.findTypesWithValidRows(1L, java.util.Set.of(5)))
            .thenReturn(java.util.Set.of());
        when(localProjectionDomainService.findTypeDefinitionResourceIds(1L, java.util.Set.of("resource_type:HR_ORG")))
            .thenReturn(java.util.List.of(88L));
        when(rolePermMapper.selectRoleIdsByResourceIds(1L, java.util.List.of(88L)))
            .thenReturn(java.util.Set.of(5L));
        when(rolePermMapper.selectValidPermIdsByResourceIds(1L, java.util.List.of(88L)))
            .thenReturn(java.util.List.of(77L));
        when(apiMappingMapper.selectByResourceEntityIds(1L, java.util.Set.of(88L)))
            .thenReturn(java.util.List.of());

        service.deleteTypesByIds(1L, java.util.List.of(9L), 100L);

        // 软删顺序对齐 deleteResources：类型行 → 投影行 → 授权行，同事务
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(
            typeDefinitionMapper, resourceEntityDomainService, rolePermMapper);
        order.verify(typeDefinitionMapper).softDeleteBatch(eq(1L), any(), any());
        order.verify(resourceEntityDomainService).softDeleteBatch(eq(1L), eq(java.util.List.of(88L)), any());
        order.verify(rolePermMapper).softDeleteBatch(eq(1L), eq(java.util.List.of(77L)), any());
    }

    @Test
    void shouldFailClosedWhenDeletingNonexistentIdsWithoutPermission() {
        // 载行空集（id 全部不存在/已删）退化为类型级校验：无权限仍抛 SecurityException
        // （保持旧实现的 fail-closed 可观察行为，防止迁移后变成静默 no-op）
        when(typeDefinitionMapper.selectValidByIds(1L, java.util.Set.of(404L))).thenReturn(java.util.List.of());
        when(engine.hasPermissionByCode(eq(1L), eq(100L), any(), eq((String) null), any()))
            .thenReturn(false);

        assertThrows(SecurityException.class,
            () -> service.deleteTypesByIds(1L, java.util.List.of(404L), 100L));

        // 有类型级权限：静默 no-op（不触删除）
        when(engine.hasPermissionByCode(eq(1L), eq(100L), any(), eq((String) null), any()))
            .thenReturn(true);
        service.deleteTypesByIds(1L, java.util.List.of(404L), 100L);
        verify(typeDefinitionMapper, never()).softDeleteBatch(anyLong(), any(), any());
    }

    // ========== T-PERM-050：resource_type 删除级联操作行 + 类型级授权行 ==========

    @Test
    void shouldCascadeOperationsAndTypeLevelGrantsOnResourceTypeDelete() {
        // 2026-09-09 定案级联：类型软删同事务级联该类型操作定义行（含预置四操作位）+ 类型级
        // 授权行；旧实现（零级联）下两条 softDeleteBatch 均不会被触达，本用例必红
        when(engine.getDeniedResourceCodes(anyLong(), anyLong(), any(), any(), any()))
            .thenReturn(java.util.Set.of());
        TypeDefinition hrOrg = new TypeDefinition();
        hrOrg.setId(9L);
        hrOrg.setTenantId(1L);
        hrOrg.setTypeKey("resource_type");
        hrOrg.setTypeCode("HR_ORG");
        hrOrg.setTypeValue(5);
        hrOrg.setIsSystem(false);
        when(typeDefinitionMapper.selectValidByIds(1L, java.util.Set.of(9L))).thenReturn(java.util.List.of(hrOrg));
        when(resourceEntityDomainService.findTypesWithValidRows(1L, java.util.Set.of(5)))
            .thenReturn(java.util.Set.of());
        cn.ac.fage.accessmesh.access.permission.entity.OperationPermission create =
            new cn.ac.fage.accessmesh.access.permission.entity.OperationPermission();
        create.setId(31L);
        when(operationPermissionMapper.selectByTenantAndResourceTypes(1L, java.util.Set.of(5)))
            .thenReturn(java.util.List.of(create));
        when(rolePermMapper.selectRoleIdsByResourceTypes(1L, java.util.Set.of(5)))
            .thenReturn(java.util.Set.of(7L));
        when(rolePermMapper.selectValidPermIdsByResourceTypes(1L, java.util.Set.of(5)))
            .thenReturn(java.util.List.of(77L));

        service.deleteTypesByIds(1L, java.util.List.of(9L), 100L);

        // 同事务软删顺序：类型行 → 操作行 → 类型级授权行
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(
            typeDefinitionMapper, operationPermissionMapper, rolePermMapper);
        order.verify(typeDefinitionMapper).softDeleteBatch(eq(1L), any(), any());
        order.verify(operationPermissionMapper).softDeleteBatch(eq(1L), eq(java.util.List.of(31L)), any());
        order.verify(rolePermMapper).softDeleteBatch(eq(1L), eq(java.util.List.of(77L)), any());
        // 受影响角色与授权行均按被删类型值批量定位（循环单查违反 §8.4.8）
        verify(rolePermMapper).selectRoleIdsByResourceTypes(1L, java.util.Set.of(5));
        verify(rolePermMapper).selectValidPermIdsByResourceTypes(1L, java.util.Set.of(5));
        // 操作集合变更提交后按被删类型 per-type 失效（T-PERM-047 终态复用）
        verify(cacheService).evictBatchAfterCommit(
            cn.ac.fage.accessmesh.access.permission.cache.PermCacheCatalog.OPERATION_PERMISSIONS_BY_TYPE,
            1L, java.util.Set.of("op_perm:5"));
    }

    @Test
    void shouldNotTouchResourceTypeFacesWhenDeletingOtherTypeKeys() {
        // 级联面限定 typeKey=resource_type：type_value 仅 tenant+type_key 内唯一，user_type
        // 同值（5）删除不得误伤 resource_type 空间的操作行/授权行——若级联按 typeValue
        // 全 typeKey 展开，本用例必红。T-PERM-056 后 user_type 删除须先过主体行数守卫
        // （无有效引用行放行，删除照常完成）
        when(engine.getDeniedResourceCodes(anyLong(), anyLong(), any(), any(), any()))
            .thenReturn(java.util.Set.of());
        TypeDefinition contractor = new TypeDefinition();
        contractor.setId(9L);
        contractor.setTenantId(1L);
        contractor.setTypeKey("user_type");
        contractor.setTypeCode("CONTRACTOR");
        contractor.setTypeValue(5);
        contractor.setIsSystem(false);
        when(typeDefinitionMapper.selectValidByIds(1L, java.util.Set.of(9L))).thenReturn(java.util.List.of(contractor));
        when(subjectDomainService.findUserTypesWithValidRows(1L, java.util.Set.of(5)))
            .thenReturn(java.util.Set.of());

        service.deleteTypesByIds(1L, java.util.List.of(9L), 100L);

        verify(typeDefinitionMapper).softDeleteBatch(eq(1L), any(), any());
        verify(operationPermissionMapper, never()).selectByTenantAndResourceTypes(anyLong(), any());
        verify(operationPermissionMapper, never()).softDeleteBatch(anyLong(), any(), any());
        verify(rolePermMapper, never()).selectRoleIdsByResourceTypes(anyLong(), any());
        verify(rolePermMapper, never()).selectValidPermIdsByResourceTypes(anyLong(), any());
        verify(cacheService, never()).evictBatchAfterCommit(
            eq(cn.ac.fage.accessmesh.access.permission.cache.PermCacheCatalog.OPERATION_PERMISSIONS_BY_TYPE),
            anyLong(), any());
        verify(subjectDomainService, never()).findRoleTypesWithValidRows(anyLong(), any());
    }

    // ========== T-PERM-056：user_type/role_type 删除引用面行数守卫（2026-09-09 用户定案删除保护） ==========

    @Test
    void shouldRejectUserTypeDeleteWhenReferencedByValidUsers() {
        // user_type 下存在有效 abstract_user 行时整批拒绝（20056，对齐 resource_type 行数守卫
        // 先例——用户/角色是业务主体数据不级联）；旧实现（零检查）下删除照常完成不抛异常，
        // 本用例必红
        when(engine.getDeniedResourceCodes(anyLong(), anyLong(), any(), any(), any()))
            .thenReturn(java.util.Set.of());
        TypeDefinition contractor = new TypeDefinition();
        contractor.setId(9L);
        contractor.setTenantId(1L);
        contractor.setTypeKey("user_type");
        contractor.setTypeCode("CONTRACTOR");
        contractor.setTypeValue(5);
        contractor.setIsSystem(false);
        when(typeDefinitionMapper.selectValidByIds(1L, java.util.Set.of(9L))).thenReturn(java.util.List.of(contractor));
        when(subjectDomainService.findUserTypesWithValidRows(1L, java.util.Set.of(5)))
            .thenReturn(java.util.Set.of(5));

        BizException ex = assertThrows(BizException.class,
            () -> service.deleteTypesByIds(1L, java.util.List.of(9L), 100L));

        assertEquals(PermissionErrorCode.TYPE_OWNERSHIP_CHANGE_CONFLICT.getCode(), ex.getErrorCode());
        // 整批拒绝：类型行/投影行零写入
        verify(typeDefinitionMapper, never()).softDeleteBatch(anyLong(), any(), any());
        verify(resourceEntityDomainService, never()).softDeleteBatch(anyLong(), any(), any());
        // 守卫按 typeKey 分族一次批量查询（循环单查违反 §8.4.8）；user_type 删除不持 RESOURCE_ENTITY 锁
        verify(subjectDomainService).findUserTypesWithValidRows(1L, java.util.Set.of(5));
        verify(subjectDomainService, never()).findRoleTypesWithValidRows(anyLong(), any());
        verify(treeWriteLockSupport, never()).lockTreeWrites(anyLong(), any());
    }

    @Test
    void shouldRejectRoleTypeDeleteWhenReferencedByValidRolesAndTakeRoleTreeLock() {
        // role_type 定案同款；自定义 role_type 角色行的唯一写入口（角色同步）持 ABSTRACT_ROLE
        // 树写锁，批删含 role_type 须共持同锁闭合「守卫查零行→并发建角色→删除落库」交错
        when(engine.getDeniedResourceCodes(anyLong(), anyLong(), any(), any(), any()))
            .thenReturn(java.util.Set.of());
        TypeDefinition customRoleType = new TypeDefinition();
        customRoleType.setId(9L);
        customRoleType.setTenantId(1L);
        customRoleType.setTypeKey("role_type");
        customRoleType.setTypeCode("CUSTOM_RT");
        customRoleType.setTypeValue(7);
        customRoleType.setIsSystem(false);
        when(typeDefinitionMapper.selectValidByIds(1L, java.util.Set.of(9L))).thenReturn(java.util.List.of(customRoleType));
        when(subjectDomainService.findRoleTypesWithValidRows(1L, java.util.Set.of(7)))
            .thenReturn(java.util.Set.of(7));

        BizException ex = assertThrows(BizException.class,
            () -> service.deleteTypesByIds(1L, java.util.List.of(9L), 100L));

        assertEquals(PermissionErrorCode.TYPE_OWNERSHIP_CHANGE_CONFLICT.getCode(), ex.getErrorCode());
        verify(treeWriteLockSupport).lockTreeWrites(1L,
            cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport.TreeLockTarget.ABSTRACT_ROLE);
        verify(treeWriteLockSupport, never()).lockTreeWrites(1L,
            cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport.TreeLockTarget.RESOURCE_ENTITY);
        verify(typeDefinitionMapper, never()).softDeleteBatch(anyLong(), any(), any());
        verify(subjectDomainService, never()).findUserTypesWithValidRows(anyLong(), any());
    }

    @Test
    void shouldSkipSubjectGuardsWhenDeletingResourceTypeOnly() {
        // 分族防误伤：纯 resource_type 批删不触主体守卫查询（type_value 仅 tenant+type_key 内
        // 唯一，user_type 与 resource_type 同值不串）——若守卫按 typeValue 跨族展开，本用例必红
        when(engine.getDeniedResourceCodes(anyLong(), anyLong(), any(), any(), any()))
            .thenReturn(java.util.Set.of());
        TypeDefinition hrOrg = new TypeDefinition();
        hrOrg.setId(9L);
        hrOrg.setTenantId(1L);
        hrOrg.setTypeKey("resource_type");
        hrOrg.setTypeCode("HR_ORG");
        hrOrg.setTypeValue(5);
        hrOrg.setIsSystem(false);
        when(typeDefinitionMapper.selectValidByIds(1L, java.util.Set.of(9L))).thenReturn(java.util.List.of(hrOrg));
        when(resourceEntityDomainService.findTypesWithValidRows(1L, java.util.Set.of(5)))
            .thenReturn(java.util.Set.of());

        service.deleteTypesByIds(1L, java.util.List.of(9L), 100L);

        verify(subjectDomainService, never()).findUserTypesWithValidRows(anyLong(), any());
        verify(subjectDomainService, never()).findRoleTypesWithValidRows(anyLong(), any());
        verify(typeDefinitionMapper).softDeleteBatch(eq(1L), any(), any());
    }
}
