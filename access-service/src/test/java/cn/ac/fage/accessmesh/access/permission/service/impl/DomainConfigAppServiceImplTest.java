package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.access.permission.dto.req.DomainConfigReq;
import cn.ac.fage.accessmesh.access.permission.dto.resp.DomainConfigResp;
import cn.ac.fage.accessmesh.access.permission.entity.BizDomain;
import cn.ac.fage.accessmesh.access.permission.entity.DomainConfig;
import cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.access.permission.mapper.BizDomainMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.DomainConfigMapper;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.access.permission.util.OperatorContext;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * T-PERM-026 收口回归 + T-PERM-046 增量：save 域解析走 FOR UPDATE 行锁
 * （selectByCodeForUpdate，与 biz-domain remove 互斥）、insert 并发双插由
 * uk_domain_config 唯一索引兜底（DIVE 映射 20058 提示重试）。
 */
@ExtendWith(MockitoExtension.class)
class DomainConfigAppServiceImplTest {

    @Mock private DomainConfigMapper domainConfigMapper;
    @Mock private BizDomainMapper bizDomainMapper;
    @Mock private TypeResolutionService typeResolutionService;
    @Mock private PermQueryEngine engine;

    private DomainConfigAppServiceImpl service;

    private static ValidatorFactory validatorFactory;

    @BeforeAll
    static void initValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
    }

    @AfterAll
    static void closeValidator() {
        if (validatorFactory != null) {
            validatorFactory.close();
        }
    }

    @BeforeEach
    void setUp() {
        service = new DomainConfigAppServiceImpl(domainConfigMapper, bizDomainMapper, typeResolutionService, engine);
    }

    private BizDomain domain(Long id, String code) {
        BizDomain domain = new BizDomain();
        domain.setId(id);
        domain.setTenantId(1L);
        domain.setCode(code);
        domain.setGlobal(false);
        return domain;
    }

    @Test
    void shouldUpsertDomainConfigWhenPermissionGranted() {
        try (MockedStatic<OperatorContext> ctx = mockStatic(OperatorContext.class)) {
            ctx.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByCode(eq(1L), eq(100L), any(), eq((String) null), any()))
                .thenReturn(true);
            when(bizDomainMapper.selectByCodeForUpdate(1L, "HR")).thenReturn(domain(10L, "HR"));
            when(domainConfigMapper.selectValidByTypeString(1L, 10L, "CLASSIFY")).thenReturn(null);

            DomainConfigReq req = new DomainConfigReq("HR", "CLASSIFY", "{\"resourceTypeCodes\":[\"USER\"]}");
            DomainConfigResp result = service.upsertDomainConfig(1L, req);

            ArgumentCaptor<DomainConfig> captor = ArgumentCaptor.forClass(DomainConfig.class);
            verify(domainConfigMapper).insert(captor.capture());
            DomainConfig inserted = captor.getValue();

            assertNotNull(result);
            assertEquals("CLASSIFY", inserted.getConfigType());
            // T-PERM-046 回归锁：域解析必须走 FOR UPDATE 行锁版本（与 remove 互斥），
            // 退化回无锁 resolveDomainId 会让删除保护并发窗口复现——旧实现下本断言失败
            verify(bizDomainMapper).selectByCodeForUpdate(1L, "HR");
            verify(typeResolutionService, never()).resolveDomainId(anyLong(), anyString());
        }
    }

    @Test
    void shouldMapUniqueIndexViolationOnInsertTo20058() {
        try (MockedStatic<OperatorContext> ctx = mockStatic(OperatorContext.class)) {
            ctx.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByCode(eq(1L), eq(100L), any(), eq((String) null), any()))
                .thenReturn(true);
            when(bizDomainMapper.selectByCodeForUpdate(1L, "HR")).thenReturn(domain(10L, "HR"));
            when(domainConfigMapper.selectValidByTypeString(1L, 10L, "CLASSIFY")).thenReturn(null);
            // check-then-insert 并发窗口：并发同键保存后落库者命中 uk_domain_config（T-PERM-046）
            when(domainConfigMapper.insert(any(DomainConfig.class)))
                .thenThrow(new DataIntegrityViolationException(
                    "duplicate key value violates unique constraint \"uk_domain_config\""));

            BizException ex = assertThrows(BizException.class,
                () -> service.upsertDomainConfig(1L, new DomainConfigReq("HR", "CLASSIFY", "{}")));
            assertEquals(PermissionErrorCode.DOMAIN_CONFIG_CONCURRENT_CONFLICT.getCode(), ex.getErrorCode());
        }
    }

    @Test
    void shouldRethrowNonUniqueIndexDiveOnInsert() {
        try (MockedStatic<OperatorContext> ctx = mockStatic(OperatorContext.class)) {
            ctx.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByCode(eq(1L), eq(100L), any(), eq((String) null), any()))
                .thenReturn(true);
            when(bizDomainMapper.selectByCodeForUpdate(1L, "HR")).thenReturn(domain(10L, "HR"));
            when(domainConfigMapper.selectValidByTypeString(1L, 10L, "CLASSIFY")).thenReturn(null);
            when(domainConfigMapper.insert(any(DomainConfig.class)))
                .thenThrow(new DataIntegrityViolationException("NOT NULL violation on other column"));

            assertThrows(DataIntegrityViolationException.class,
                () -> service.upsertDomainConfig(1L, new DomainConfigReq("HR", "CLASSIFY", "{}")));
        }
    }

    @Test
    void shouldThrowWhenUpsertDomainConfigPermissionDenied() {
        try (MockedStatic<OperatorContext> ctx = mockStatic(OperatorContext.class)) {
            ctx.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByCode(eq(1L), eq(100L), any(), eq((String) null), any()))
                .thenReturn(false);

            DomainConfigReq req = new DomainConfigReq("HR", "SUB_PERM", "{}");
            assertThrows(SecurityException.class, () -> service.upsertDomainConfig(1L, req));
        }
    }

    @Test
    void shouldRejectUnimplementedConfigTypeOnSave() {
        Validator validator = validatorFactory.getValidator();

        // 已实现两类：校验通过
        for (String allowed : new String[] {"CLASSIFY", "SUB_PERM"}) {
            Set<ConstraintViolation<DomainConfigReq>> violations =
                validator.validate(new DomainConfigReq("HR", allowed, "{}"));
            assertTrue(violations.isEmpty(), allowed + " 应通过白名单校验");
        }

        // 历史设想类型与任意值：写入校验拒绝（防止未实现类型入库形成脏数据）
        for (String rejected : new String[] {"SCOPE", "RELATION", "BINDING", "classify", ""}) {
            Set<ConstraintViolation<DomainConfigReq>> violations =
                validator.validate(new DomainConfigReq("HR", rejected, "{}"));
            assertFalse(violations.isEmpty(), rejected + " 应被白名单校验拒绝");
        }
    }

    @Test
    void shouldRejectInvalidExtraJsonOnSave() {
        try (MockedStatic<OperatorContext> ctx = mockStatic(OperatorContext.class)) {
            ctx.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByCode(eq(1L), eq(100L), any(), eq((String) null), any()))
                .thenReturn(true);

            // 非法 JSON 在权限校验后、触达数据前 fail-closed（system-config configValue 同范式，
            // T-PERM-026 补齐；否则打到 PG JSONB 解析错误裸 99999）
            assertThrows(IllegalArgumentException.class,
                () -> service.upsertDomainConfig(1L, new DomainConfigReq("HR", "CLASSIFY", "{not-json")));
            verify(domainConfigMapper, never()).insert(any(DomainConfig.class));
        }
    }
}
