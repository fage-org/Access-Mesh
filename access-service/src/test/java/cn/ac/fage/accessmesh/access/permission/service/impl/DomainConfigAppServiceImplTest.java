package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.access.permission.dto.req.DomainConfigReq;
import cn.ac.fage.accessmesh.access.permission.dto.resp.DomainConfigResp;
import cn.ac.fage.accessmesh.access.permission.entity.DomainConfig;
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

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DomainConfigAppServiceImplTest {

    @Mock private DomainConfigMapper domainConfigMapper;
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
        service = new DomainConfigAppServiceImpl(domainConfigMapper, typeResolutionService, engine);
    }

    @Test
    void shouldUpsertDomainConfigWhenPermissionGranted() {
        try (MockedStatic<OperatorContext> ctx = mockStatic(OperatorContext.class)) {
            ctx.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByCode(eq(1L), eq(100L), any(), eq((String) null), any()))
                .thenReturn(true);
            when(typeResolutionService.resolveDomainId(1L, "HR")).thenReturn(10L);
            when(domainConfigMapper.selectValidByTypeString(1L, 10L, "CLASSIFY")).thenReturn(null);

            DomainConfigReq req = new DomainConfigReq("HR", "CLASSIFY", "{\"typeCodes\":[\"USER\"]}");
            DomainConfigResp result = service.upsertDomainConfig(1L, req);

            ArgumentCaptor<DomainConfig> captor = ArgumentCaptor.forClass(DomainConfig.class);
            verify(domainConfigMapper).insert(captor.capture());
            DomainConfig inserted = captor.getValue();

            assertNotNull(result);
            assertEquals("CLASSIFY", inserted.getConfigType());
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
