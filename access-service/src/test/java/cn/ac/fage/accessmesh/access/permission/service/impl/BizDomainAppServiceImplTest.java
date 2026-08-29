package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.access.permission.dto.req.BizDomainCreateReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.BizDomainUpdateReq;
import cn.ac.fage.accessmesh.access.permission.dto.resp.BizDomainResp;
import cn.ac.fage.accessmesh.access.permission.entity.BizDomain;
import cn.ac.fage.accessmesh.access.permission.entity.DomainConfig;
import cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.access.permission.mapper.BizDomainMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.DomainConfigMapper;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.access.permission.util.OperatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T-PERM-026 收口回归：业务键 code 定位（detail/update）、list 服务端过滤分页、
 * Resp global 字段、create 编码查重（预查 + uk_biz_domain DIVE 兜底）、
 * remove 删除保护（全局域不可删 + 域配置引用检查拒删，20051）。
 */
@ExtendWith(MockitoExtension.class)
class BizDomainAppServiceImplTest {

    @Mock private BizDomainMapper bizDomainMapper;
    @Mock private DomainConfigMapper domainConfigMapper;
    @Mock private PermQueryEngine engine;

    private BizDomainAppServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new BizDomainAppServiceImpl(bizDomainMapper, domainConfigMapper, engine);
    }

    private BizDomain entity(Long id, String code, boolean global) {
        BizDomain domain = new BizDomain();
        domain.setId(id);
        domain.setTenantId(1L);
        domain.setCode(code);
        domain.setName(code + " 域");
        domain.setGlobal(global);
        return domain;
    }

    // ===== create =====

    @Test
    void shouldCreateBizDomainWhenPermissionGranted() {
        when(engine.hasPermissionByCode(eq(1L), eq(100L), any(), eq((String) null), any()))
            .thenReturn(true);
        when(bizDomainMapper.selectByCode(1L, "HR")).thenReturn(null);

        BizDomainCreateReq req = new BizDomainCreateReq("HR", "人力资源", "desc");
        BizDomainResp result = service.createBizDomain(1L, req, 100L);

        ArgumentCaptor<BizDomain> captor = ArgumentCaptor.forClass(BizDomain.class);
        verify(bizDomainMapper).insert(captor.capture());
        BizDomain inserted = captor.getValue();

        assertNotNull(result);
        assertEquals("HR", inserted.getCode());
        assertEquals("人力资源", inserted.getName());
        // API 创建固定普通域（全局域不由本入口创建）
        assertEquals(Boolean.FALSE, inserted.getGlobal());
        assertEquals(Boolean.FALSE, result.global());
    }

    @Test
    void shouldThrowWhenCreateBizDomainPermissionDenied() {
        when(engine.hasPermissionByCode(eq(1L), eq(100L), any(), eq((String) null), any()))
            .thenReturn(false);

        BizDomainCreateReq req = new BizDomainCreateReq("HR", "人力资源", "desc");
        assertThrows(SecurityException.class, () -> service.createBizDomain(1L, req, 100L));
    }

    @Test
    void shouldRejectCreateWithDuplicateCode() {
        when(engine.hasPermissionByCode(eq(1L), eq(100L), any(), eq((String) null), any()))
            .thenReturn(true);
        when(bizDomainMapper.selectByCode(1L, "HR")).thenReturn(entity(10L, "HR", false));

        BizException ex = assertThrows(BizException.class,
            () -> service.createBizDomain(1L, new BizDomainCreateReq("HR", "人力资源", null), 100L));
        assertEquals(PermissionErrorCode.DOMAIN_CODE_DUPLICATE.getCode(), ex.getErrorCode());
        verify(bizDomainMapper, never()).insert(any(BizDomain.class));
    }

    @Test
    void shouldMapUniqueIndexViolationOnCreateTo20052() {
        when(engine.hasPermissionByCode(eq(1L), eq(100L), any(), eq((String) null), any()))
            .thenReturn(true);
        when(bizDomainMapper.selectByCode(1L, "HR")).thenReturn(null);
        // check-then-insert 并发窗口：DB 唯一索引兜底（TypeDefinition/ConflictRule 同模式）
        when(bizDomainMapper.insert(any(BizDomain.class)))
            .thenThrow(new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"uk_biz_domain\""));

        BizException ex = assertThrows(BizException.class,
            () -> service.createBizDomain(1L, new BizDomainCreateReq("HR", "人力资源", null), 100L));
        assertEquals(PermissionErrorCode.DOMAIN_CODE_DUPLICATE.getCode(), ex.getErrorCode());
    }

    @Test
    void shouldRethrowNonUniqueIndexDiveOnCreate() {
        when(engine.hasPermissionByCode(eq(1L), eq(100L), any(), eq((String) null), any()))
            .thenReturn(true);
        when(bizDomainMapper.selectByCode(1L, "HR")).thenReturn(null);
        when(bizDomainMapper.insert(any(BizDomain.class)))
            .thenThrow(new DataIntegrityViolationException("NOT NULL violation on other column"));

        assertThrows(DataIntegrityViolationException.class,
            () -> service.createBizDomain(1L, new BizDomainCreateReq("HR", "人力资源", null), 100L));
    }

    @Test
    void shouldNotMapGlobalUniqueIndexViolationTo20052() {
        when(engine.hasPermissionByCode(eq(1L), eq(100L), any(), eq((String) null), any()))
            .thenReturn(true);
        when(bizDomainMapper.selectByCode(1L, "HR")).thenReturn(null);
        // uk_biz_domain 是 uk_biz_domain_global 的前缀：裸子串匹配会误吞全局域唯一索引违例
        // 映射成 20052「编码重复」，带引号精确匹配下应原样重抛（当前 create 固定 global=false
        // 不可达，防御性回归锁——旧实现下本用例失败）
        when(bizDomainMapper.insert(any(BizDomain.class)))
            .thenThrow(new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"uk_biz_domain_global\""));

        assertThrows(DataIntegrityViolationException.class,
            () -> service.createBizDomain(1L, new BizDomainCreateReq("HR", "人力资源", null), 100L));
    }

    // ===== detail（业务键 code + 类型级 DOMAIN:VIEW 门禁）=====

    @Test
    void shouldReturnDomainWithGlobalFlagOnDetail() {
        try (MockedStatic<OperatorContext> ctx = mockStaticOperator()) {
            when(engine.hasPermissionByCode(eq(1L), eq(100L), any(), eq((String) null), any()))
                .thenReturn(true);
            when(bizDomainMapper.selectByCode(1L, "GLOBAL")).thenReturn(entity(10L, "GLOBAL", true));

            BizDomainResp result = service.getBizDomain(1L, "GLOBAL");
            assertNotNull(result);
            assertEquals("GLOBAL", result.code());
            assertEquals(Boolean.TRUE, result.global());
        }
    }

    @Test
    void shouldReturnNullForUnknownDomainCodeOnDetail() {
        try (MockedStatic<OperatorContext> ctx = mockStaticOperator()) {
            when(engine.hasPermissionByCode(eq(1L), eq(100L), any(), eq((String) null), any()))
                .thenReturn(true);
            when(bizDomainMapper.selectByCode(1L, "NOPE")).thenReturn(null);

            // 未知编码与已删除域同口径 data=null 不抛错（role detail 先例）
            assertNull(service.getBizDomain(1L, "NOPE"));
        }
    }

    @Test
    void shouldThrowWhenDetailPermissionDenied() {
        try (MockedStatic<OperatorContext> ctx = mockStaticOperator()) {
            when(engine.hasPermissionByCode(eq(1L), eq(100L), any(), eq((String) null), any()))
                .thenReturn(false);

            assertThrows(SecurityException.class, () -> service.getBizDomain(1L, "HR"));
            // 门禁先于查询：无权不触库，也不泄露编码存在性
            verify(bizDomainMapper, never()).selectByCode(anyLong(), anyString());
        }
    }

    // ===== list/count（服务端 keyword 过滤 + 分页）=====

    @Test
    void shouldNormalizeKeywordAndPassPagingParamsOnListAndCount() {
        try (MockedStatic<OperatorContext> ctx = mockStaticOperator()) {
            when(engine.hasPermissionByCode(eq(1L), eq(100L), any(), eq((String) null), any()))
                .thenReturn(true);
            when(bizDomainMapper.countByCondition(1L, null)).thenReturn(0L);
            when(bizDomainMapper.countByCondition(1L, "HR")).thenReturn(1L);
            when(bizDomainMapper.selectPageByCondition(eq(1L), eq("HR"), eq(15), eq(0)))
                .thenReturn(List.of(entity(10L, "HR", false)));

            // 空白 keyword 归一为 null（与 SQL <if> 判空语义一致）
            assertEquals(0L, service.countBizDomains(1L, "   "));
            verify(bizDomainMapper).countByCondition(1L, null);
            assertEquals(1L, service.countBizDomains(1L, " HR "));

            List<BizDomainResp> items = service.listBizDomains(1L, " HR ", 0, 15);
            assertEquals(1, items.size());
            verify(bizDomainMapper).selectPageByCondition(1L, "HR", 15, 0);
        }
    }

    @Test
    void shouldThrowWhenListPermissionDenied() {
        try (MockedStatic<OperatorContext> ctx = mockStaticOperator()) {
            when(engine.hasPermissionByCode(eq(1L), eq(100L), any(), eq((String) null), any()))
                .thenReturn(false);

            assertThrows(SecurityException.class, () -> service.countBizDomains(1L, null));
            assertThrows(SecurityException.class, () -> service.listBizDomains(1L, null, 0, 15));
        }
    }

    // ===== update（业务键 code 定位；name/description null=不更新、空串=清空）=====

    @Test
    void shouldUpdateDomainByCodeWithNullSkipAndEmptyClear() {
        when(engine.hasPermissionByCode(eq(1L), eq(100L), any(), eq((String) null), any()))
            .thenReturn(true);
        BizDomain existing = entity(10L, "HR", false);
        existing.setName("旧名");
        existing.setDescription("旧描述");
        when(bizDomainMapper.selectByCode(1L, "HR")).thenReturn(existing);

        BizDomainResp result = service.updateBizDomain(1L,
            new BizDomainUpdateReq("HR", null, ""), 100L);

        ArgumentCaptor<BizDomain> captor = ArgumentCaptor.forClass(BizDomain.class);
        verify(bizDomainMapper).update(captor.capture());
        // name=null 不更新；description="" 显式清空
        assertEquals("旧名", captor.getValue().getName());
        assertEquals("", captor.getValue().getDescription());
        assertEquals("旧名", result.name());
    }

    @Test
    void shouldThrowUpdateUnknownDomainCode() {
        when(engine.hasPermissionByCode(eq(1L), eq(100L), any(), eq((String) null), any()))
            .thenReturn(true);
        when(bizDomainMapper.selectByCode(1L, "NOPE")).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
            () -> service.updateBizDomain(1L, new BizDomainUpdateReq("NOPE", "新名", null), 100L));
        assertEquals(PermissionErrorCode.DOMAIN_NOT_FOUND.getCode(), ex.getErrorCode());
    }

    // ===== remove（删除保护：全局域不可删 + 域配置引用检查拒删，20051）=====

    @Test
    void shouldRejectRemoveGlobalDomain() {
        when(engine.hasPermissionByCode(eq(1L), eq(100L), any(), eq((String) null), any()))
            .thenReturn(true);
        when(bizDomainMapper.selectValidByIds(eq(1L), anySet()))
            .thenReturn(List.of(entity(10L, "GLOBAL", true)));

        BizException ex = assertThrows(BizException.class,
            () -> service.deleteBizDomainsByIds(1L, List.of(10L), 100L));
        assertEquals(PermissionErrorCode.DOMAIN_DELETE_CONFLICT.getCode(), ex.getErrorCode());
        assertTrue(ex.getMessage().contains("GLOBAL"));
        verify(bizDomainMapper, never()).softDeleteBatch(anyLong(), any(), any());
    }

    @Test
    void shouldRejectRemoveWhenDomainConfigsReferenced() {
        when(engine.hasPermissionByCode(eq(1L), eq(100L), any(), eq((String) null), any()))
            .thenReturn(true);
        when(bizDomainMapper.selectValidByIds(eq(1L), anySet()))
            .thenReturn(List.of(entity(10L, "HR", false)));
        DomainConfig config = new DomainConfig();
        config.setId(99L);
        config.setBizDomainId(10L);
        config.setConfigType("CLASSIFY");
        when(domainConfigMapper.selectValidByDomainIds(eq(1L), anySet()))
            .thenReturn(List.of(config));

        BizException ex = assertThrows(BizException.class,
            () -> service.deleteBizDomainsByIds(1L, List.of(10L), 100L));
        assertEquals(PermissionErrorCode.DOMAIN_DELETE_CONFLICT.getCode(), ex.getErrorCode());
        verify(bizDomainMapper, never()).softDeleteBatch(anyLong(), any(), any());
    }

    @Test
    void shouldSoftDeleteDomainsWithoutConflicts() {
        when(engine.hasPermissionByCode(eq(1L), eq(100L), any(), eq((String) null), any()))
            .thenReturn(true);
        when(bizDomainMapper.selectValidByIds(eq(1L), anySet()))
            .thenReturn(List.of(entity(10L, "HR", false), entity(11L, "ORDER", false)));
        when(domainConfigMapper.selectValidByDomainIds(eq(1L), anySet()))
            .thenReturn(List.of());

        service.deleteBizDomainsByIds(1L, List.of(10L, 11L, 999L), 100L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> idsCaptor = ArgumentCaptor.forClass(List.class);
        verify(bizDomainMapper).softDeleteBatch(eq(1L), idsCaptor.capture(), any());
        // 仅有效域落删（未知 id 999 不在库内被过滤）
        assertEquals(Set.of(10L, 11L), Set.copyOf(idsCaptor.getValue()));
    }

    private MockedStatic<OperatorContext> mockStaticOperator() {
        MockedStatic<OperatorContext> ctx = org.mockito.Mockito.mockStatic(OperatorContext.class);
        ctx.when(OperatorContext::getOperatorId).thenReturn(100L);
        return ctx;
    }
}
