package cn.ac.fage.accessmesh.access.permission.service.domain.impl;

import cn.ac.fage.accessmesh.access.permission.entity.BizDomain;
import cn.ac.fage.accessmesh.access.permission.entity.DomainConfig;
import cn.ac.fage.accessmesh.access.permission.entity.TypeDefinition;
import cn.ac.fage.accessmesh.access.permission.enums.DomainQueryMode;
import cn.ac.fage.accessmesh.access.permission.mapper.BizDomainMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.DomainConfigMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.TypeDefinitionMapper;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DomainClassifyServiceImplTest {

    @Mock private BizDomainMapper bizDomainMapper;
    @Mock private DomainConfigMapper domainConfigMapper;
    @Mock private TypeDefinitionMapper typeDefinitionMapper;
    @Mock private TypeResolutionService typeResolutionService;

    private DomainClassifyServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DomainClassifyServiceImpl(
            bizDomainMapper,
            domainConfigMapper,
            typeDefinitionMapper,
            typeResolutionService,
            new ObjectMapper()
        );
    }

    @Test
    void shouldMatchSpecificAndGlobalTypesInGlobalPlusMode() {
        BizDomain opsDomain = domain(10L, 1L, false, "OPS");
        BizDomain hrDomain = domain(20L, 1L, false, "HR");

        when(typeResolutionService.resolveDomainId(1L, "OPS")).thenReturn(10L);
        when(typeResolutionService.resolveTypeValue(1L, "resource_type", "MENU")).thenReturn(1);
        when(typeResolutionService.resolveTypeValue(1L, "resource_type", "BUTTON")).thenReturn(2);
        when(typeResolutionService.resolveTypeValue(1L, "resource_type", "API")).thenReturn(3);
        when(bizDomainMapper.selectValidById(10L, 1L)).thenReturn(opsDomain);
        when(bizDomainMapper.selectNonGlobalByTenant(any())).thenReturn(List.of(opsDomain, hrDomain));
        when(domainConfigMapper.selectValidByTypeString(eq(1L), eq(10L), anyString())).thenReturn(classifyConfig("MENU"));
        when(domainConfigMapper.selectValidByTypeString(eq(1L), eq(20L), anyString())).thenReturn(classifyConfig("BUTTON"));

        assertTrue(service.matchesTypeCode(1L, DomainQueryMode.GLOBAL_PLUS, "OPS", "MENU"));
        assertFalse(service.matchesTypeCode(1L, DomainQueryMode.GLOBAL_PLUS, "OPS", "BUTTON"));
        assertTrue(service.matchesTypeCode(1L, DomainQueryMode.GLOBAL_PLUS, "OPS", "API"));
    }

    @Test
    void shouldReturnFalseWhenDomainDoesNotExist() {
        when(typeResolutionService.resolveTypeValue(1L, "resource_type", "MENU")).thenReturn(1);
        when(typeResolutionService.resolveDomainId(1L, "UNKNOWN")).thenReturn(null);

        assertFalse(service.matchesTypeCode(1L, DomainQueryMode.GLOBAL_PLUS, "UNKNOWN", "MENU"));
    }

    @Test
    void shouldOnlyMatchUnclaimedTypesForGlobalDomainInGlobalPlusMode() {
        BizDomain opsDomain = domain(10L, 1L, false, "OPS");
        BizDomain globalDomain = domain(99L, 1L, true, "GLOBAL");

        when(typeResolutionService.resolveTypeValue(1L, "resource_type", "MENU")).thenReturn(1);
        when(typeResolutionService.resolveTypeValue(1L, "resource_type", "API")).thenReturn(2);
        when(typeResolutionService.resolveDomainId(1L, "GLOBAL")).thenReturn(99L);
        when(bizDomainMapper.selectValidById(99L, 1L)).thenReturn(globalDomain);
        when(bizDomainMapper.selectNonGlobalByTenant(1L)).thenReturn(List.of(opsDomain));
        when(domainConfigMapper.selectValidByTypeString(eq(1L), eq(10L), anyString())).thenReturn(classifyConfig("MENU"));
        // 全局域自身无 CLASSIFY 声明（T-PERM-046：新路径会显式查全局域声明行，null=退回动态补集）
        when(domainConfigMapper.selectValidByTypeString(eq(1L), eq(99L), anyString())).thenReturn(null);
        when(typeDefinitionMapper.selectByTenantAndTypeKey(1L, "resource_type")).thenReturn(List.of(
            typeDefinition("MENU"),
            typeDefinition("API")
        ));

        assertFalse(service.matchesTypeCode(1L, DomainQueryMode.GLOBAL_PLUS, "GLOBAL", "MENU"));
        assertTrue(service.matchesTypeCode(1L, DomainQueryMode.GLOBAL_PLUS, "GLOBAL", "API"));
    }

    @Test
    void shouldReturnFalseForUnknownResourceTypeCodeEvenInAllMode() {
        when(typeResolutionService.resolveTypeValue(1L, "resource_type", "UNKNOWN")).thenReturn(null);

        assertFalse(service.matchesTypeCode(1L, DomainQueryMode.ALL, null, "UNKNOWN"));
    }

    @Test
    void shouldFallbackToGlobalDomainWhenTypeIsUnclaimed() {
        BizDomain opsDomain = domain(10L, 1L, false, "OPS");
        BizDomain globalDomain = domain(99L, 1L, true, "GLOBAL");
        DomainConfig config = classifyConfig("MENU");
        config.setBizDomainId(10L);
        config.setConfigType("CLASSIFY");

        when(bizDomainMapper.selectNonGlobalByTenant(any())).thenReturn(List.of(opsDomain));
        when(domainConfigMapper.selectByTenantId(any())).thenReturn(List.of(config));
        when(bizDomainMapper.selectGlobalByTenant(any())).thenReturn(globalDomain);

        assertEquals(99L, service.findDomainIdByTypeCode(1L, "API"));
    }

    // ===== T-PERM-046：全局域 CLASSIFY 声明生效（2026-09-09 用户定案）=====

    @Test
    void shouldUseDeclaredClassifyForGlobalDomainWhenPresent() {
        BizDomain opsDomain = domain(10L, 1L, false, "OPS");
        BizDomain globalDomain = domain(99L, 1L, true, "GLOBAL");

        when(typeResolutionService.resolveDomainId(1L, "GLOBAL")).thenReturn(99L);
        when(bizDomainMapper.selectValidById(99L, 1L)).thenReturn(globalDomain);
        // 全局域自身声明 MENU（无声明时应走补集的路径由既有用例覆盖）
        when(domainConfigMapper.selectValidByTypeString(eq(1L), eq(99L), anyString()))
            .thenReturn(classifyConfig("MENU"));

        // getClassifiedTypeCodes：有声明按声明（不再走动态补集——API 不在声明集即被收窄排除）
        assertEquals(Set.of("MENU"), service.getClassifiedTypeCodes(1L, "GLOBAL"));
    }

    @Test
    void shouldNarrowGlobalPlusImplicitScopeToGlobalDeclaration() {
        BizDomain opsDomain = domain(10L, 1L, false, "OPS");
        BizDomain globalDomain = domain(99L, 1L, true, "GLOBAL");

        when(typeResolutionService.resolveDomainId(1L, "OPS")).thenReturn(10L);
        when(typeResolutionService.resolveTypeValue(1L, "resource_type", "MENU")).thenReturn(1);
        when(typeResolutionService.resolveTypeValue(1L, "resource_type", "API")).thenReturn(2);
        when(bizDomainMapper.selectValidById(10L, 1L)).thenReturn(opsDomain);
        when(domainConfigMapper.selectValidByTypeString(eq(1L), eq(10L), anyString()))
            .thenReturn(classifyConfig("POSITION"));
        // 全局域声明 MENU：GLOBAL_PLUS 隐式段从「未被认领的补集」收窄为声明集
        // （有声明即不走补集——selectNonGlobalByTenant 不会被调用，无需 stub）
        when(bizDomainMapper.selectGlobalByTenant(1L)).thenReturn(globalDomain);
        when(domainConfigMapper.selectValidByTypeString(eq(1L), eq(99L), anyString()))
            .thenReturn(classifyConfig("MENU"));

        // OPS 域 GLOBAL_PLUS：全局域声明的 MENU 命中隐式段
        assertTrue(service.matchesTypeCode(1L, DomainQueryMode.GLOBAL_PLUS, "OPS", "MENU"));
        // 未被任何域认领的 API：全局域有声明后不再落入隐式段（声明即收窄，旧行为会命中）
        assertFalse(service.matchesTypeCode(1L, DomainQueryMode.GLOBAL_PLUS, "OPS", "API"));
    }

    private BizDomain domain(Long id, Long tenantId, boolean global, String code) {
        BizDomain domain = new BizDomain();
        domain.setId(id);
        domain.setTenantId(tenantId);
        domain.setGlobal(global);
        domain.setCode(code);
        return domain;
    }

    private DomainConfig classifyConfig(String... typeCodes) {
        DomainConfig config = new DomainConfig();
        List<String> codes = new ArrayList<>();
        for (String typeCode : typeCodes) {
            codes.add('"' + typeCode + '"');
        }
        config.setExtra("{\"resourceTypeCodes\":[" + String.join(",", codes) + "]}");
        return config;
    }

    private TypeDefinition typeDefinition(String typeCode) {
        TypeDefinition typeDefinition = new TypeDefinition();
        typeDefinition.setTypeCode(typeCode);
        return typeDefinition;
    }
}
