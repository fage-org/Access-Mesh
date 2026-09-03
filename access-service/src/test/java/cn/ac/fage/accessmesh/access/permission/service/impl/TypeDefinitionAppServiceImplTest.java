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

    private TypeDefinitionAppServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TypeDefinitionAppServiceImpl(
            typeDefinitionMapper, operationPermissionMapper, engine
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
        // typeCode 留空 → TYPEKEY_<typeValue> 生成
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
        when(engine.hasPermissionByCode(eq(1L), eq(100L), any(), eq("99"), any()))
            .thenReturn(true);
        when(typeDefinitionMapper.selectValidById(1L, 99L)).thenReturn(null);

        TypeUpdateReq req = new TypeUpdateReq(99L, "Updated", "desc", 1, null);

        BizException exception = assertThrows(BizException.class, () -> service.updateType(1L, req, 100L));

        assertEquals(PermissionErrorCode.TYPE_DEFINITION_NOT_FOUND.getCode(), exception.getErrorCode());
    }

    @Test
    void shouldListTypesWhenOnlyInstanceLevelViewGranted() {
        // 2026-09-03 门禁放宽：类型级拒绝但任一实例级 VIEW 命中即可查询（与登录权限串口径对齐；
        // 旧实现仅认类型级，此用例必红）
        when(engine.hasPermissionByCode(eq(1L), eq(100L), any(), eq((String) null), any()))
            .thenReturn(false);
        when(typeDefinitionMapper.selectValidCodesByTenant(1L))
            .thenReturn(List.of("USER_TYPE", "ROLE_TYPE", "RESOURCE_TYPE"));
        when(engine.getDeniedResourceCodes(eq(1L), eq(100L), any(), any(), any()))
            .thenReturn(java.util.Set.of("USER_TYPE", "ROLE_TYPE"));
        when(typeDefinitionMapper.selectPageByCondition(eq(1L), isNull(), isNull(), anyInt(), anyInt()))
            .thenReturn(List.of());

        assertEquals(List.of(), service.listTypes(1L, null, null, 0, 200));
        verify(typeDefinitionMapper).selectPageByCondition(eq(1L), isNull(), isNull(), eq(200), eq(0));
    }

    @Test
    void shouldThrowWhenAllInstancesDeniedAndTypeLevelDenied() {
        when(engine.hasPermissionByCode(eq(1L), eq(100L), any(), eq((String) null), any()))
            .thenReturn(false);
        when(typeDefinitionMapper.selectValidCodesByTenant(1L))
            .thenReturn(List.of("USER_TYPE", "ROLE_TYPE"));
        when(engine.getDeniedResourceCodes(eq(1L), eq(100L), any(), any(), any()))
            .thenReturn(java.util.Set.of("USER_TYPE", "ROLE_TYPE"));

        assertThrows(SecurityException.class, () -> service.listTypes(1L, null, null, 0, 200));
        verify(typeDefinitionMapper, never()).selectPageByCondition(anyLong(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void shouldThrowWhenAllInstancesDeniedAndCodesContainCrossKeyDuplicates() {
        // 种子跨 type_key 重码（user_type/resource_type 均有 USER、SERVICE）：全拒判定必须按
        // 去重码集比较——旧实现 denied(去重) >= codes(含重复) 恒 false，零权限账号 fail-open
        when(engine.hasPermissionByCode(eq(1L), eq(100L), any(), eq((String) null), any()))
            .thenReturn(false);
        when(typeDefinitionMapper.selectValidCodesByTenant(1L))
            .thenReturn(List.of("USER", "SERVICE", "USER", "SERVICE", "ORG"));
        when(engine.getDeniedResourceCodes(eq(1L), eq(100L), any(), any(), any()))
            .thenReturn(new java.util.LinkedHashSet<>(List.of("USER", "SERVICE", "ORG")));

        assertThrows(SecurityException.class, () -> service.listTypes(1L, null, null, 0, 200));
        verify(typeDefinitionMapper, never()).selectPageByCondition(anyLong(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void shouldThrowWhenNoTypeInstancesExistAndTypeLevelDenied() {
        // 无实例可判定时 fail-closed（空清单无法证明任何实例级授权）
        when(engine.hasPermissionByCode(eq(1L), eq(100L), any(), eq((String) null), any()))
            .thenReturn(false);
        when(typeDefinitionMapper.selectValidCodesByTenant(1L))
            .thenReturn(List.of());

        assertThrows(SecurityException.class, () -> service.countTypes(1L, null, null));
    }
}
