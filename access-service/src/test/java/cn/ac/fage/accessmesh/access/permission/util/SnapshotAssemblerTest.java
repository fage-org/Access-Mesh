package cn.ac.fage.accessmesh.access.permission.util;

import cn.ac.fage.accessmesh.perm.common.dto.resp.InterfaceSnapshotResp.ApiPermissionEntry;
import cn.ac.fage.accessmesh.perm.common.enums.ScopeMode;
import cn.ac.fage.accessmesh.access.permission.dto.query.PermResult;
import cn.ac.fage.accessmesh.access.permission.entity.PermissionCondition;
import cn.ac.fage.accessmesh.access.permission.entity.ResourceApiMapping;
import cn.ac.fage.accessmesh.access.permission.mapper.PermissionConditionMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.ResourceApiMappingMapper;
import cn.ac.fage.accessmesh.access.permission.vo.RolePermEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * {@link SnapshotAssembler} 单元测试
 * <p>
 * T-PERM-017 C3：聚焦条件规则内联逻辑——gateway_evaluable 标志 + isGatewayPushable 防御性过滤。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class SnapshotAssemblerTest {

    private static final long TENANT_ID = 1L;
    private static final String SERVICE_CODE = "example-service";
    private static final Integer API_TYPE = 2;
    private static final Long RESOURCE_ID = 100L;
    private static final Long CONDITION_ID = 9L;

    @Mock private ResourceApiMappingMapper apiMappingMapper;
    @Mock private PermissionConditionMapper conditionMapper;
    @Mock private cn.ac.fage.accessmesh.access.permission.mapper.OperationPermissionMapper operationPermissionMapper;

    private SnapshotAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = new SnapshotAssembler(apiMappingMapper, conditionMapper, operationPermissionMapper,
            new ObjectMapper());
        // ACCESS 操作位固定为 1（与 DDL 种子位无耦合——测试只验证按位过滤逻辑本身）
        cn.ac.fage.accessmesh.access.permission.entity.OperationPermission access =
            new cn.ac.fage.accessmesh.access.permission.entity.OperationPermission();
        access.setCode("ACCESS");
        access.setBinaryBit(1L);
        org.mockito.Mockito.lenient().when(operationPermissionMapper.selectByTenantAndResourceType(
                org.mockito.ArgumentMatchers.eq(TENANT_ID), org.mockito.ArgumentMatchers.any()))
            .thenReturn(java.util.List.of(access));
    }

    @Test
    void shouldInlineRules_whenGatewayEvaluableAndPushable() {
        String rules = "{\"logic\":\"AND\",\"items\":["
            + "{\"type\":\"IP_WHITELIST\",\"params\":{\"cidrs\":[\"10.0.0.0/8\"]}}]}";
        PermissionCondition cond = newCondition(true, rules);
        when(conditionMapper.selectValidByIds(eq(TENANT_ID), any())).thenReturn(List.of(cond));
        when(apiMappingMapper.selectEnabledByServiceCode(eq(TENANT_ID), eq(SERVICE_CODE)))
            .thenReturn(List.of(apiMapping("POST", "/api/order")));

        PermResult result = resultWith(entryWithCondition(RESOURCE_ID, CONDITION_ID));

        List<ApiPermissionEntry> entries = assembler.buildSnapshot(TENANT_ID, result, SERVICE_CODE, API_TYPE);

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).hasCondition()).isTrue();
        assertThat(entries.get(0).conditionId()).isEqualTo(CONDITION_ID);
        assertThat(entries.get(0).conditionRules()).isEqualTo(rules);
    }

    @Test
    void shouldNotInlineRules_whenGatewayEvaluableFalse() {
        String rules = "{\"logic\":\"AND\",\"items\":["
            + "{\"type\":\"IP_WHITELIST\",\"params\":{\"cidrs\":[\"10.0.0.0/8\"]}}]}";
        PermissionCondition cond = newCondition(false, rules);
        when(conditionMapper.selectValidByIds(eq(TENANT_ID), any())).thenReturn(List.of(cond));
        when(apiMappingMapper.selectEnabledByServiceCode(eq(TENANT_ID), eq(SERVICE_CODE)))
            .thenReturn(List.of(apiMapping("POST", "/api/order")));

        PermResult result = resultWith(entryWithCondition(RESOURCE_ID, CONDITION_ID));

        List<ApiPermissionEntry> entries = assembler.buildSnapshot(TENANT_ID, result, SERVICE_CODE, API_TYPE);

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).hasCondition()).isTrue();
        assertThat(entries.get(0).conditionRules()).isNull();   // Gateway 走 fallback
    }

    @Test
    void shouldDefensivelyRejectInline_whenRulesContainUnknownType() {
        // DB 误存 gateway_evaluable=true 但规则含未知类型（绕过 C2.5 写入门禁的脏数据）
        String rules = "{\"logic\":\"AND\",\"items\":["
            + "{\"type\":\"ORG_SCOPE\",\"params\":{\"orgIds\":[1]}}]}";
        PermissionCondition cond = newCondition(true, rules);
        when(conditionMapper.selectValidByIds(eq(TENANT_ID), any())).thenReturn(List.of(cond));
        when(apiMappingMapper.selectEnabledByServiceCode(eq(TENANT_ID), eq(SERVICE_CODE)))
            .thenReturn(List.of(apiMapping("POST", "/api/order")));

        PermResult result = resultWith(entryWithCondition(RESOURCE_ID, CONDITION_ID));

        List<ApiPermissionEntry> entries = assembler.buildSnapshot(TENANT_ID, result, SERVICE_CODE, API_TYPE);

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).hasCondition()).isTrue();
        assertThat(entries.get(0).conditionRules()).isNull();   // 防御过滤拒绝内联
    }

    @Test
    void shouldKeepBothEntries_whenSameApiHasUnconditionalAndConditionalGrants() {
        // T-PERM-017 C4 修 P1-②：同一 API 资源同时持有"无条件"和"含条件"两条授权（不同角色）
        // 必须保留为两条独立的 ApiPermissionEntry，Gateway InterfaceSnapshotMatcher 用 OR 语义合并：
        // 任一分支放行即允许。原实现 anyMatch+findFirst 会折叠成单条带条件 entry，
        // 条件评估失败时整体拒绝，丢失无条件分支授权。
        String rules = "{\"logic\":\"AND\",\"items\":["
            + "{\"type\":\"IP_WHITELIST\",\"params\":{\"cidrs\":[\"10.0.0.0/8\"]}}]}";
        PermissionCondition cond = newCondition(true, rules);
        when(conditionMapper.selectValidByIds(eq(TENANT_ID), any())).thenReturn(List.of(cond));
        when(apiMappingMapper.selectEnabledByServiceCode(eq(TENANT_ID), eq(SERVICE_CODE)))
            .thenReturn(List.of(apiMapping("POST", "/api/order")));

        // 角色A：无条件授权；角色B：含条件授权（IP 白名单）
        RolePermEntry unconditional = new RolePermEntry(
            1L, 10L, RESOURCE_ID, "res-code", API_TYPE, 1L, "ACCESS", 1L,
            "DIRECT", true, null, false, null, false);
        RolePermEntry conditional = entryWithCondition(RESOURCE_ID, CONDITION_ID);
        PermResult result = resultWith(unconditional, conditional);

        List<ApiPermissionEntry> entries = assembler.buildSnapshot(TENANT_ID, result, SERVICE_CODE, API_TYPE);

        assertThat(entries).hasSize(2);
        ApiPermissionEntry unconditionalEntry = entries.stream()
            .filter(e -> !e.hasCondition()).findFirst().orElseThrow();
        ApiPermissionEntry conditionalEntry = entries.stream()
            .filter(ApiPermissionEntry::hasCondition).findFirst().orElseThrow();
        assertThat(unconditionalEntry.conditionId()).isNull();
        assertThat(unconditionalEntry.conditionRules()).isNull();
        assertThat(conditionalEntry.conditionId()).isEqualTo(CONDITION_ID);
        assertThat(conditionalEntry.conditionRules()).isEqualTo(rules);
    }

    @Test
    void shouldDedupSameConditionMultiRole_whenSameApiSameConditionMultipleGrants() {
        // 同一资源、同一 conditionId、不同角色的多条授权对 Gateway 匹配没有区别，去重为一条 entry。
        // 与上一个 case 形成对比：不同 conditionId 才保留多条。
        String rules = "{\"logic\":\"AND\",\"items\":["
            + "{\"type\":\"IP_WHITELIST\",\"params\":{\"cidrs\":[\"10.0.0.0/8\"]}}]}";
        PermissionCondition cond = newCondition(true, rules);
        when(conditionMapper.selectValidByIds(eq(TENANT_ID), any())).thenReturn(List.of(cond));
        when(apiMappingMapper.selectEnabledByServiceCode(eq(TENANT_ID), eq(SERVICE_CODE)))
            .thenReturn(List.of(apiMapping("POST", "/api/order")));

        RolePermEntry roleA = new RolePermEntry(
            1L, 10L, RESOURCE_ID, "res-code", API_TYPE, 1L, "ACCESS", 1L,
            "DIRECT", true, CONDITION_ID, true, null, false);
        RolePermEntry roleB = new RolePermEntry(
            2L, 20L, RESOURCE_ID, "res-code", API_TYPE, 1L, "ACCESS", 1L,
            "DIRECT", true, CONDITION_ID, true, null, false);
        PermResult result = resultWith(roleA, roleB);

        List<ApiPermissionEntry> entries = assembler.buildSnapshot(TENANT_ID, result, SERVICE_CODE, API_TYPE);

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).conditionId()).isEqualTo(CONDITION_ID);
    }

    @Test
    void shouldExcludeNonAccessOperations_fromSnapshot() {
        // 快照路径与 fallback check-interface 同口径：仅 ACCESS 操作码构成 Gateway 放行依据，
        // API 资源的 VIEW/UPDATE 等非 ACCESS 授权是资源管理语义，不得被网关当作接口放行
        when(apiMappingMapper.selectEnabledByServiceCode(eq(TENANT_ID), eq(SERVICE_CODE)))
            .thenReturn(List.of(apiMapping("POST", "/api/order")));

        RolePermEntry viewOnly = new RolePermEntry(
            1L, 10L, RESOURCE_ID, "res-code", API_TYPE, 2L, "VIEW", 2L,
            "DIRECT", true, null, false, null, false);
        RolePermEntry viewScopeAll = new RolePermEntry(
            2L, 20L, null, null, API_TYPE, 2L, "VIEW", 2L,
            "DIRECT", true, null, false, null, true);
        RolePermEntry accessEntry = new RolePermEntry(
            3L, 30L, RESOURCE_ID, "res-code", API_TYPE, 1L, "ACCESS", 1L,
            "DIRECT", true, null, false, null, false);
        PermResult result = PermResult.builder(true, "ok")
            .instanceEntries(List.of(viewOnly, accessEntry))
            .scopeAllEntries(List.of(viewScopeAll))
            .build();

        List<ApiPermissionEntry> entries = assembler.buildSnapshot(TENANT_ID, result, SERVICE_CODE, API_TYPE);

        // 仅 ACCESS 实例条目进入快照；VIEW 实例与 VIEW scopeAll 均被排除
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).scopeMode()).isEqualTo(ScopeMode.INSTANCE);
    }

    @Test
    void shouldExpandScopeAll_toEnabledMappingsAsInstance() {
        // 类型级 API 授权语义=「全部已注册 API」：scopeAll 条目展开为该 serviceCode 全部
        // enabled 映射的 INSTANCE 条目（含 method/path），不输出 ALL 通配——未注册接口
        // 维持默认拒绝（§14.2 禁止 API 类型级 scopeAll 大包授权）
        when(apiMappingMapper.selectEnabledByServiceCode(eq(TENANT_ID), eq(SERVICE_CODE)))
            .thenReturn(List.of(
                apiMapping("POST", "/api/order"),
                apiMapping("GET", "/api/order")));

        RolePermEntry scopeAll = new RolePermEntry(
            1L, 10L, null, null, API_TYPE, 1L, null, null,
            "DIRECT", true, null, false, null, true);
        PermResult result = PermResult.builder(true, "ok")
            .scopeAllEntries(List.of(scopeAll))
            .build();

        List<ApiPermissionEntry> entries = assembler.buildSnapshot(TENANT_ID, result, SERVICE_CODE, API_TYPE);

        assertThat(entries).hasSize(2);
        assertThat(entries).allMatch(e -> e.scopeMode() == ScopeMode.INSTANCE);
        assertThat(entries).anyMatch(e -> "POST".equals(e.httpMethod()) && "/api/order".equals(e.pathPattern()));
        assertThat(entries).anyMatch(e -> "GET".equals(e.httpMethod()) && "/api/order".equals(e.pathPattern()));
    }

    @Test
    void shouldExpandScopeAllPerMapping_whenConditional() {
        // 含条件的 scopeAll 授权：每个映射保留条件语义（条件规则内联或置 null 走 fallback）
        String rules = "{\"logic\":\"AND\",\"items\":["
            + "{\"type\":\"IP_WHITELIST\",\"params\":{\"cidrs\":[\"10.0.0.0/8\"]}}]}";
        PermissionCondition cond = newCondition(true, rules);
        when(conditionMapper.selectValidByIds(eq(TENANT_ID), any())).thenReturn(List.of(cond));
        when(apiMappingMapper.selectEnabledByServiceCode(eq(TENANT_ID), eq(SERVICE_CODE)))
            .thenReturn(List.of(apiMapping("POST", "/api/order"), apiMapping("GET", "/api/order")));

        RolePermEntry scopeAll = new RolePermEntry(
            1L, 10L, null, null, API_TYPE, 1L, null, null,
            "DIRECT", true, CONDITION_ID, true, null, true);
        PermResult result = PermResult.builder(true, "ok")
            .scopeAllEntries(List.of(scopeAll))
            .build();

        List<ApiPermissionEntry> entries = assembler.buildSnapshot(TENANT_ID, result, SERVICE_CODE, API_TYPE);

        assertThat(entries).hasSize(2);
        assertThat(entries).allMatch(e -> e.hasCondition() && CONDITION_ID.equals(e.conditionId())
            && rules.equals(e.conditionRules()));
    }

    @Test
    void shouldHonorInheritedAccessBit_viaInheritMask() {
        // 继承位语义与 fallback 引擎一致：自定义操作 effectiveBits(binaryBit|inheritMask)
        // 覆盖 ACCESS 位时，持该操作（grantedBits 只含其 binaryBit）同样构成接口放行
        cn.ac.fage.accessmesh.access.permission.entity.OperationPermission access =
            new cn.ac.fage.accessmesh.access.permission.entity.OperationPermission();
        access.setCode("ACCESS");
        access.setBinaryBit(1L);
        // MANAGE_API 位 4，inheritMask=1（继承 ACCESS）→ effectiveBits=5
        cn.ac.fage.accessmesh.access.permission.entity.OperationPermission manageApi =
            new cn.ac.fage.accessmesh.access.permission.entity.OperationPermission();
        manageApi.setCode("MANAGE_API");
        manageApi.setBinaryBit(4L);
        manageApi.setInheritMask(1L);
        org.mockito.Mockito.when(operationPermissionMapper.selectByTenantAndResourceType(
                org.mockito.ArgumentMatchers.eq(TENANT_ID), org.mockito.ArgumentMatchers.any()))
            .thenReturn(java.util.List.of(access, manageApi));
        when(apiMappingMapper.selectEnabledByServiceCode(eq(TENANT_ID), eq(SERVICE_CODE)))
            .thenReturn(List.of(apiMapping("POST", "/api/order")));

        RolePermEntry inheritedOnly = new RolePermEntry(
            1L, 10L, RESOURCE_ID, "res-code", API_TYPE, 4L, "MANAGE_API", 4L,
            "DIRECT", true, null, false, null, false);
        PermResult result = resultWith(inheritedOnly);

        List<ApiPermissionEntry> entries = assembler.buildSnapshot(TENANT_ID, result, SERVICE_CODE, API_TYPE);

        assertThat(entries).as("经 inheritMask 继承 ACCESS 的操作必须进入快照").hasSize(1);
    }

    // ===== helpers =====

    private PermissionCondition newCondition(boolean gatewayEvaluable, String rules) {
        PermissionCondition c = new PermissionCondition();
        c.setId(CONDITION_ID);
        c.setTenantId(TENANT_ID);
        c.setConditionRules(rules);
        c.setEnabled(true);
        c.setGatewayEvaluable(gatewayEvaluable);
        c.setDeleteFlag(0L);
        return c;
    }

    private RolePermEntry entryWithCondition(Long resourceEntityId, Long conditionId) {
        return new RolePermEntry(
            1L,            // permissionId
            10L,           // roleId
            resourceEntityId,
            "res-code",    // resourceCode
            API_TYPE,      // resourceType
            1L,            // grantedBits
            "ACCESS",      // operationCode
            1L,            // effectiveBits
            "DIRECT",      // grantSource
            true,          // canGrant
            conditionId,
            true,          // hasCondition
            null,          // dependOn
            false          // scopeAll
        );
    }

    @Test
    void shouldExcludeDependentEntriesFromSnapshot() {
        // T-PERM-058：接口快照无主资源上下文——depend_on 子权限行不下发 Gateway
        // （子权限授权只在 query-scopes 主资源上下文内生效，接口鉴权面不消费）
        when(apiMappingMapper.selectEnabledByServiceCode(eq(TENANT_ID), eq(SERVICE_CODE)))
            .thenReturn(List.of(apiMapping("POST", "/api/order")));

        RolePermEntry dependent = new RolePermEntry(
            1L, 10L, RESOURCE_ID, "res-code", API_TYPE, 1L, "ACCESS", 1L,
            "DIRECT", false, null, false, 501L, false);
        // 仅子行授权：快照为空（旧实现子行照常下发=接口鉴权绕过父绑定）
        assertThat(assembler.buildSnapshot(TENANT_ID, resultWith(dependent), SERVICE_CODE, API_TYPE))
            .isEmpty();

        // 主行照常下发：排除不误伤
        RolePermEntry main = new RolePermEntry(
            2L, 10L, RESOURCE_ID, "res-code", API_TYPE, 1L, "ACCESS", 1L,
            "DIRECT", false, null, false, null, false);
        assertThat(assembler.buildSnapshot(TENANT_ID, resultWith(main, dependent), SERVICE_CODE, API_TYPE))
            .hasSize(1);
    }

    private ResourceApiMapping apiMapping(String httpMethod, String pathPattern) {
        ResourceApiMapping m = new ResourceApiMapping();
        m.setTenantId(TENANT_ID);
        m.setServiceCode(SERVICE_CODE);
        m.setResourceEntityId(RESOURCE_ID);
        m.setHttpMethod(httpMethod);
        m.setPathPattern(pathPattern);
        return m;
    }

    private PermResult resultWith(RolePermEntry... entries) {
        // PermResult.allEntries() = scopeAllEntries + instanceEntries；测试用实例级条目
        return PermResult.builder(true, "ok")
            .instanceEntries(List.of(entries))
            .build();
    }
}
