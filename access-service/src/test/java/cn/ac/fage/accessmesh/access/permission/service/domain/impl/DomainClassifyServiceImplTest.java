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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
        // T-PERM-055：认领集收敛为单次 selectByTenantId 装载（原 per-domain selectValidByTypeString 循环退役）
        when(domainConfigMapper.selectByTenantId(any())).thenReturn(List.of(
            classifyConfigWithDomain(10L, "MENU"), classifyConfigWithDomain(20L, "BUTTON")));

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
        // 全局域自身无 CLASSIFY 声明（T-PERM-046：新路径会显式查全局域声明行，null=退回动态补集）
        when(domainConfigMapper.selectValidByTypeString(eq(1L), eq(99L), anyString())).thenReturn(null);
        // T-PERM-055：认领集收敛为单次 selectByTenantId 装载
        when(domainConfigMapper.selectByTenantId(1L)).thenReturn(List.of(
            classifyConfigWithDomain(10L, "MENU")));
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

    // ===== T-PERM-055：批量预载对拍回归锁（预载 contains == 逐条 matchesTypeCode）=====

    /** 全局域三态之一：有 CLASSIFY 声明——隐式段收窄为声明集，对拍两侧判定一致。 */
    @Test
    void shouldPreloadCoveredTypeCodesConsistentWhenGlobalDomainDeclared() {
        BizDomain opsDomain = domain(10L, 1L, false, "OPS");
        BizDomain globalDomain = domain(99L, 1L, true, "GLOBAL");

        when(typeResolutionService.resolveDomainId(1L, "OPS")).thenReturn(10L);
        when(typeResolutionService.resolveTypeValue(1L, "resource_type", "MENU")).thenReturn(1);
        when(typeResolutionService.resolveTypeValue(1L, "resource_type", "BUTTON")).thenReturn(2);
        when(typeResolutionService.resolveTypeValue(1L, "resource_type", "API")).thenReturn(3);
        when(typeResolutionService.resolveTypeValue(1L, "resource_type", "CONDITION")).thenReturn(4);
        when(typeResolutionService.resolveTypeValue(1L, "resource_type", "GHOST")).thenReturn(null);
        when(bizDomainMapper.selectValidById(10L, 1L)).thenReturn(opsDomain);
        when(domainConfigMapper.selectValidByTypeString(eq(1L), eq(10L), anyString())).thenReturn(classifyConfig("MENU"));
        when(bizDomainMapper.selectGlobalByTenant(1L)).thenReturn(globalDomain);
        // 全局域声明 API：GLOBAL_PLUS 隐式段从动态补集收窄为 {API}（T-PERM-046）
        when(domainConfigMapper.selectValidByTypeString(eq(1L), eq(99L), anyString())).thenReturn(classifyConfig("API"));
        when(typeDefinitionMapper.selectByTenantAndTypeKey(1L, "resource_type")).thenReturn(List.of(
            typeDefinition("MENU"), typeDefinition("BUTTON"), typeDefinition("API"), typeDefinition("CONDITION")
        ));

        Set<String> covered = service.preloadCoveredTypeCodes(1L, DomainQueryMode.GLOBAL_PLUS, "OPS");
        // 语义锚点：未声明的有效类型 CONDITION 被收窄排除（旧行为会经补集命中）
        assertTrue(covered.contains("API"));
        assertFalse(covered.contains("CONDITION"));
        assertCrosscheckMatchesTypeCode(covered, DomainQueryMode.GLOBAL_PLUS, "OPS",
            "MENU", "BUTTON", "API", "CONDITION", "GHOST", "");
    }

    /** 全局域三态之二：无声明退回动态补集；同时锁认领集单次装配（N+1 收敛回归锁）。 */
    @Test
    void shouldPreloadCoveredTypeCodesConsistentOnDynamicComplement() {
        BizDomain opsDomain = domain(10L, 1L, false, "OPS");
        BizDomain hrDomain = domain(20L, 1L, false, "HR");
        BizDomain globalDomain = domain(99L, 1L, true, "GLOBAL");

        when(typeResolutionService.resolveDomainId(1L, "OPS")).thenReturn(10L);
        when(typeResolutionService.resolveTypeValue(1L, "resource_type", "MENU")).thenReturn(1);
        when(typeResolutionService.resolveTypeValue(1L, "resource_type", "BUTTON")).thenReturn(2);
        when(typeResolutionService.resolveTypeValue(1L, "resource_type", "API")).thenReturn(3);
        when(typeResolutionService.resolveTypeValue(1L, "resource_type", "GHOST")).thenReturn(null);
        when(bizDomainMapper.selectValidById(10L, 1L)).thenReturn(opsDomain);
        when(bizDomainMapper.selectNonGlobalByTenant(1L)).thenReturn(List.of(opsDomain, hrDomain));
        when(domainConfigMapper.selectValidByTypeString(eq(1L), eq(10L), anyString())).thenReturn(classifyConfig("MENU"));
        when(bizDomainMapper.selectGlobalByTenant(1L)).thenReturn(globalDomain);
        when(domainConfigMapper.selectValidByTypeString(eq(1L), eq(99L), anyString())).thenReturn(null);
        when(domainConfigMapper.selectByTenantId(1L)).thenReturn(List.of(
            classifyConfigWithDomain(10L, "MENU"), classifyConfigWithDomain(20L, "BUTTON")));
        when(typeDefinitionMapper.selectByTenantAndTypeKey(1L, "resource_type")).thenReturn(List.of(
            typeDefinition("MENU"), typeDefinition("BUTTON"), typeDefinition("API")
        ));

        Set<String> covered = service.preloadCoveredTypeCodes(1L, DomainQueryMode.GLOBAL_PLUS, "OPS");
        // N+1 收敛回归锁：认领集单次 selectByTenantId 装载；selectValidByTypeString 仅目标域+全局域两条
        verify(domainConfigMapper, times(1)).selectByTenantId(any());
        verify(domainConfigMapper, times(2)).selectValidByTypeString(any(), any(), any());
        // 语义锚点：OPS 自身声明 MENU 可见，HR 认领的 BUTTON 不可见，未认领 API 落补集可见
        assertTrue(covered.contains("MENU"));
        assertFalse(covered.contains("BUTTON"));
        assertTrue(covered.contains("API"));
        assertCrosscheckMatchesTypeCode(covered, DomainQueryMode.GLOBAL_PLUS, "OPS",
            "MENU", "BUTTON", "API", "GHOST", "");
    }

    /** 全局域三态之三：全局域不存在——隐式段仍为未被认领补集，对拍两侧判定一致。 */
    @Test
    void shouldPreloadCoveredTypeCodesConsistentWhenNoGlobalDomain() {
        BizDomain opsDomain = domain(10L, 1L, false, "OPS");
        BizDomain hrDomain = domain(20L, 1L, false, "HR");

        when(typeResolutionService.resolveDomainId(1L, "OPS")).thenReturn(10L);
        when(typeResolutionService.resolveTypeValue(1L, "resource_type", "MENU")).thenReturn(1);
        when(typeResolutionService.resolveTypeValue(1L, "resource_type", "BUTTON")).thenReturn(2);
        when(typeResolutionService.resolveTypeValue(1L, "resource_type", "API")).thenReturn(3);
        when(bizDomainMapper.selectValidById(10L, 1L)).thenReturn(opsDomain);
        when(bizDomainMapper.selectNonGlobalByTenant(1L)).thenReturn(List.of(opsDomain, hrDomain));
        when(domainConfigMapper.selectValidByTypeString(eq(1L), eq(10L), anyString())).thenReturn(classifyConfig("MENU"));
        when(bizDomainMapper.selectGlobalByTenant(1L)).thenReturn(null);
        when(domainConfigMapper.selectByTenantId(1L)).thenReturn(List.of(
            classifyConfigWithDomain(10L, "MENU"), classifyConfigWithDomain(20L, "BUTTON")));
        when(typeDefinitionMapper.selectByTenantAndTypeKey(1L, "resource_type")).thenReturn(List.of(
            typeDefinition("MENU"), typeDefinition("BUTTON"), typeDefinition("API")
        ));

        Set<String> covered = service.preloadCoveredTypeCodes(1L, DomainQueryMode.GLOBAL_PLUS, "OPS");
        assertCrosscheckMatchesTypeCode(covered, DomainQueryMode.GLOBAL_PLUS, "OPS",
            "MENU", "BUTTON", "API", "");
    }

    /** 指定域=全局域自身·有声明：预载两侧同为声明集（锁定 preload 侧 isGlobal 分支，评审 P3-1 补强）。 */
    @Test
    void shouldPreloadCoveredTypeCodesConsistentWhenQueryingGlobalDomainItselfDeclared() {
        BizDomain globalDomain = domain(99L, 1L, true, "GLOBAL");

        when(typeResolutionService.resolveDomainId(1L, "GLOBAL")).thenReturn(99L);
        when(typeResolutionService.resolveTypeValue(1L, "resource_type", "MENU")).thenReturn(1);
        when(typeResolutionService.resolveTypeValue(1L, "resource_type", "BUTTON")).thenReturn(2);
        when(typeResolutionService.resolveTypeValue(1L, "resource_type", "API")).thenReturn(3);
        when(typeResolutionService.resolveTypeValue(1L, "resource_type", "GHOST")).thenReturn(null);
        when(bizDomainMapper.selectValidById(99L, 1L)).thenReturn(globalDomain);
        // 全局域声明 API：指定域=全局域时 classifiedCodes 与隐式段同为声明集
        when(domainConfigMapper.selectValidByTypeString(eq(1L), eq(99L), anyString())).thenReturn(classifyConfig("API"));
        when(bizDomainMapper.selectGlobalByTenant(1L)).thenReturn(globalDomain);
        when(typeDefinitionMapper.selectByTenantAndTypeKey(1L, "resource_type")).thenReturn(List.of(
            typeDefinition("MENU"), typeDefinition("BUTTON"), typeDefinition("API")
        ));

        Set<String> covered = service.preloadCoveredTypeCodes(1L, DomainQueryMode.GLOBAL_PLUS, "GLOBAL");
        assertTrue(covered.contains("API"));
        assertFalse(covered.contains("MENU"));
        assertCrosscheckMatchesTypeCode(covered, DomainQueryMode.GLOBAL_PLUS, "GLOBAL",
            "MENU", "BUTTON", "API", "GHOST", "");
    }

    /** 指定域=全局域自身·无声明：预载两侧同为未被认领补集（锁定 preload 侧 isGlobal 补集分支）。 */
    @Test
    void shouldPreloadCoveredTypeCodesConsistentWhenQueryingGlobalDomainItselfUndeclared() {
        BizDomain opsDomain = domain(10L, 1L, false, "OPS");
        BizDomain hrDomain = domain(20L, 1L, false, "HR");
        BizDomain globalDomain = domain(99L, 1L, true, "GLOBAL");

        when(typeResolutionService.resolveDomainId(1L, "GLOBAL")).thenReturn(99L);
        when(typeResolutionService.resolveTypeValue(1L, "resource_type", "MENU")).thenReturn(1);
        when(typeResolutionService.resolveTypeValue(1L, "resource_type", "BUTTON")).thenReturn(2);
        when(typeResolutionService.resolveTypeValue(1L, "resource_type", "API")).thenReturn(3);
        when(bizDomainMapper.selectValidById(99L, 1L)).thenReturn(globalDomain);
        when(bizDomainMapper.selectNonGlobalByTenant(1L)).thenReturn(List.of(opsDomain, hrDomain));
        when(domainConfigMapper.selectValidByTypeString(eq(1L), eq(99L), anyString())).thenReturn(null);
        when(bizDomainMapper.selectGlobalByTenant(1L)).thenReturn(globalDomain);
        when(domainConfigMapper.selectByTenantId(1L)).thenReturn(List.of(
            classifyConfigWithDomain(10L, "MENU"), classifyConfigWithDomain(20L, "BUTTON")));
        when(typeDefinitionMapper.selectByTenantAndTypeKey(1L, "resource_type")).thenReturn(List.of(
            typeDefinition("MENU"), typeDefinition("BUTTON"), typeDefinition("API")
        ));

        Set<String> covered = service.preloadCoveredTypeCodes(1L, DomainQueryMode.GLOBAL_PLUS, "GLOBAL");
        // 无声明退动态补集：MENU/BUTTON 被非全局域认领不可见，API 未认领可见
        assertTrue(covered.contains("API"));
        assertFalse(covered.contains("MENU"));
        assertCrosscheckMatchesTypeCode(covered, DomainQueryMode.GLOBAL_PLUS, "GLOBAL",
            "MENU", "BUTTON", "API", "");
    }

    /** 三模式与退化输入对拍：ALL/空 domainCode/DOMAIN_ONLY/域不存在。 */
    @Test
    void shouldPreloadCoveredTypeCodesConsistentAcrossModesAndEdgeInputs() {
        BizDomain opsDomain = domain(10L, 1L, false, "OPS");

        when(typeResolutionService.resolveDomainId(1L, "OPS")).thenReturn(10L);
        when(typeResolutionService.resolveDomainId(1L, "NOPE")).thenReturn(null);
        when(typeResolutionService.resolveTypeValue(1L, "resource_type", "MENU")).thenReturn(1);
        when(typeResolutionService.resolveTypeValue(1L, "resource_type", "API")).thenReturn(2);
        when(typeResolutionService.resolveTypeValue(1L, "resource_type", "GHOST")).thenReturn(null);
        when(bizDomainMapper.selectValidById(10L, 1L)).thenReturn(opsDomain);
        // 声明集掺入无效类型码 GHOST（type_definition 不存在）：预载侧靠有效交集滤除，对拍两侧同 false
        when(domainConfigMapper.selectValidByTypeString(eq(1L), eq(10L), anyString())).thenReturn(classifyConfig("MENU", "GHOST"));
        when(typeDefinitionMapper.selectByTenantAndTypeKey(1L, "resource_type")).thenReturn(List.of(
            typeDefinition("MENU"), typeDefinition("API")
        ));

        Set<String> allCovered = service.preloadCoveredTypeCodes(1L, DomainQueryMode.ALL, null);
        assertCrosscheckMatchesTypeCode(allCovered, DomainQueryMode.ALL, null, "MENU", "API", "GHOST", "");

        Set<String> blankCovered = service.preloadCoveredTypeCodes(1L, DomainQueryMode.GLOBAL_PLUS, "");
        assertCrosscheckMatchesTypeCode(blankCovered, DomainQueryMode.GLOBAL_PLUS, "", "MENU", "API", "GHOST");

        Set<String> domainOnlyCovered = service.preloadCoveredTypeCodes(1L, DomainQueryMode.DOMAIN_ONLY, "OPS");
        assertTrue(domainOnlyCovered.contains("MENU"));
        assertFalse(domainOnlyCovered.contains("API"));
        assertFalse(domainOnlyCovered.contains("GHOST"));
        assertCrosscheckMatchesTypeCode(domainOnlyCovered, DomainQueryMode.DOMAIN_ONLY, "OPS", "MENU", "API", "GHOST");

        // 域不存在：空集，与逐条判定的全 false 一致
        Set<String> unknownCovered = service.preloadCoveredTypeCodes(1L, DomainQueryMode.GLOBAL_PLUS, "NOPE");
        assertTrue(unknownCovered.isEmpty());
        assertFalse(service.matchesTypeCode(1L, DomainQueryMode.GLOBAL_PLUS, "NOPE", "MENU"));
    }

    /** 对拍断言：预载集合 contains 与逐条 matchesTypeCode 对同一探针判定必须一致。 */
    private void assertCrosscheckMatchesTypeCode(Set<String> covered, DomainQueryMode mode, String domainCode,
                                                  String... probes) {
        for (String probe : probes) {
            assertEquals(service.matchesTypeCode(1L, mode, domainCode, probe), covered.contains(probe),
                () -> "对拍不一致: mode=" + mode + ", domainCode=" + domainCode + ", probe=" + probe);
        }
        assertFalse(covered.contains(null));
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

    /** 带域归属的 CLASSIFY 配置行（T-PERM-055：认领集经 selectByTenantId 装载后的行形态）。 */
    private DomainConfig classifyConfigWithDomain(Long bizDomainId, String... typeCodes) {
        DomainConfig config = classifyConfig(typeCodes);
        config.setBizDomainId(bizDomainId);
        config.setConfigType("CLASSIFY");
        return config;
    }

    private TypeDefinition typeDefinition(String typeCode) {
        TypeDefinition typeDefinition = new TypeDefinition();
        typeDefinition.setTypeCode(typeCode);
        return typeDefinition;
    }
}
