package cn.ac.fage.accessmesh.access.engine.util;

import cn.ac.fage.accessmesh.perm.common.dto.resp.InterfaceSnapshotResp.ApiPermissionEntry;
import cn.ac.fage.accessmesh.perm.common.enums.ScopeMode;
import cn.ac.fage.accessmesh.access.engine.query.GrantFact;
import cn.ac.fage.accessmesh.access.rule.entity.PermissionCondition;
import cn.ac.fage.accessmesh.access.resource.entity.ResourceApiMapping;
import cn.ac.fage.accessmesh.access.rule.mapper.PermissionConditionMapper;
import cn.ac.fage.accessmesh.access.resource.mapper.ResourceApiMappingMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * {@link SnapshotAssembler} 单元测试
 * <p>
 * T-PERM-017 C3：聚焦条件规则内联逻辑——gateway_evaluable 标志 + isGatewayPushable 防御性过滤。
 * T-PERM-090：入参改消费新引擎保留事实 {@link GrantFact}（S01~S04 快照回归锁不变）。
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
    @Mock private cn.ac.fage.accessmesh.access.type.mapper.OperationPermissionMapper operationPermissionMapper;

    private SnapshotAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = new SnapshotAssembler(apiMappingMapper, conditionMapper, operationPermissionMapper,
            new ObjectMapper());
        // ACCESS 操作位固定为 1（与 DDL 种子位无耦合——测试只验证按位过滤逻辑本身）
        cn.ac.fage.accessmesh.access.type.entity.OperationPermission access =
            new cn.ac.fage.accessmesh.access.type.entity.OperationPermission();
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

        List<ApiPermissionEntry> entries = assembler.buildSnapshot(TENANT_ID,
            List.of(entryWithCondition(RESOURCE_ID, CONDITION_ID)), SERVICE_CODE, API_TYPE);

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

        List<ApiPermissionEntry> entries = assembler.buildSnapshot(TENANT_ID,
            List.of(entryWithCondition(RESOURCE_ID, CONDITION_ID)), SERVICE_CODE, API_TYPE);

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

        List<ApiPermissionEntry> entries = assembler.buildSnapshot(TENANT_ID,
            List.of(entryWithCondition(RESOURCE_ID, CONDITION_ID)), SERVICE_CODE, API_TYPE);

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
        GrantFact unconditional = new GrantFact(
            1L, 10L, API_TYPE, RESOURCE_ID, 1L, false, true, null, false, null, "DIRECT");
        GrantFact conditional = entryWithCondition(RESOURCE_ID, CONDITION_ID);

        List<ApiPermissionEntry> entries = assembler.buildSnapshot(TENANT_ID,
            List.of(unconditional, conditional), SERVICE_CODE, API_TYPE);

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

        GrantFact roleA = new GrantFact(
            1L, 10L, API_TYPE, RESOURCE_ID, 1L, false, true, CONDITION_ID, true, null, "DIRECT");
        GrantFact roleB = new GrantFact(
            2L, 20L, API_TYPE, RESOURCE_ID, 1L, false, true, CONDITION_ID, true, null, "DIRECT");

        List<ApiPermissionEntry> entries = assembler.buildSnapshot(TENANT_ID, List.of(roleA, roleB),
            SERVICE_CODE, API_TYPE);

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).conditionId()).isEqualTo(CONDITION_ID);
    }

    @Test
    void shouldExcludeNonAccessOperations_fromSnapshot() {
        // 快照路径与 fallback check-interface 同口径：仅 ACCESS 操作码构成 Gateway 放行依据，
        // API 资源的 VIEW/UPDATE 等非 ACCESS 授权是资源管理语义，不得被网关当作接口放行
        when(apiMappingMapper.selectEnabledByServiceCode(eq(TENANT_ID), eq(SERVICE_CODE)))
            .thenReturn(List.of(apiMapping("POST", "/api/order")));

        GrantFact viewOnly = new GrantFact(
            1L, 10L, API_TYPE, RESOURCE_ID, 2L, false, true, null, false, null, "DIRECT");
        GrantFact viewScopeAll = new GrantFact(
            2L, 20L, API_TYPE, null, 2L, true, true, null, false, null, "DIRECT");
        GrantFact accessEntry = new GrantFact(
            3L, 30L, API_TYPE, RESOURCE_ID, 1L, false, true, null, false, null, "DIRECT");

        List<ApiPermissionEntry> entries = assembler.buildSnapshot(TENANT_ID,
            List.of(viewOnly, accessEntry, viewScopeAll), SERVICE_CODE, API_TYPE);

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

        GrantFact scopeAll = new GrantFact(
            1L, 10L, API_TYPE, null, 1L, true, true, null, false, null, "DIRECT");

        List<ApiPermissionEntry> entries = assembler.buildSnapshot(TENANT_ID, List.of(scopeAll),
            SERVICE_CODE, API_TYPE);

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

        GrantFact scopeAll = new GrantFact(
            1L, 10L, API_TYPE, null, 1L, true, true, CONDITION_ID, true, null, "DIRECT");

        List<ApiPermissionEntry> entries = assembler.buildSnapshot(TENANT_ID, List.of(scopeAll),
            SERVICE_CODE, API_TYPE);

        assertThat(entries).hasSize(2);
        assertThat(entries).allMatch(e -> e.hasCondition() && CONDITION_ID.equals(e.conditionId())
            && rules.equals(e.conditionRules()));
    }

    @Test
    void shouldHonorInheritedAccessBit_viaInheritMask() {
        // 继承位语义与 fallback 引擎一致：自定义操作 effectiveBits(binaryBit|inheritMask)
        // 覆盖 ACCESS 位时，持该操作（grantedBits 只含其 binaryBit）同样构成接口放行
        cn.ac.fage.accessmesh.access.type.entity.OperationPermission access =
            new cn.ac.fage.accessmesh.access.type.entity.OperationPermission();
        access.setCode("ACCESS");
        access.setBinaryBit(1L);
        // MANAGE_API 位 4，inheritMask=1（继承 ACCESS）→ effectiveBits=5
        cn.ac.fage.accessmesh.access.type.entity.OperationPermission manageApi =
            new cn.ac.fage.accessmesh.access.type.entity.OperationPermission();
        manageApi.setCode("MANAGE_API");
        manageApi.setBinaryBit(4L);
        manageApi.setInheritMask(1L);
        org.mockito.Mockito.when(operationPermissionMapper.selectByTenantAndResourceType(
                org.mockito.ArgumentMatchers.eq(TENANT_ID), org.mockito.ArgumentMatchers.any()))
            .thenReturn(java.util.List.of(access, manageApi));
        when(apiMappingMapper.selectEnabledByServiceCode(eq(TENANT_ID), eq(SERVICE_CODE)))
            .thenReturn(List.of(apiMapping("POST", "/api/order")));

        GrantFact inheritedOnly = new GrantFact(
            1L, 10L, API_TYPE, RESOURCE_ID, 4L, false, true, null, false, null, "DIRECT");

        List<ApiPermissionEntry> entries = assembler.buildSnapshot(TENANT_ID, List.of(inheritedOnly),
            SERVICE_CODE, API_TYPE);

        assertThat(entries).as("经 inheritMask 继承 ACCESS 的操作必须进入快照").hasSize(1);
    }

    @Test
    void shouldExcludeDependentEntriesFromSnapshot() {
        // T-PERM-058：接口快照无主资源上下文——depend_on 子权限行不下发 Gateway
        // （子权限授权只在 query-scopes 主资源上下文内生效，接口鉴权面不消费）
        when(apiMappingMapper.selectEnabledByServiceCode(eq(TENANT_ID), eq(SERVICE_CODE)))
            .thenReturn(List.of(apiMapping("POST", "/api/order")));

        GrantFact dependent = new GrantFact(
            1L, 10L, API_TYPE, RESOURCE_ID, 1L, false, false, null, false, 501L, "DIRECT");
        // 仅子行授权：快照为空（旧实现子行照常下发=接口鉴权绕过父绑定）
        assertThat(assembler.buildSnapshot(TENANT_ID, List.of(dependent), SERVICE_CODE, API_TYPE))
            .isEmpty();

        // 主行照常下发：排除不误伤
        GrantFact main = new GrantFact(
            2L, 10L, API_TYPE, RESOURCE_ID, 1L, false, false, null, false, null, "DIRECT");
        assertThat(assembler.buildSnapshot(TENANT_ID, List.of(main, dependent), SERVICE_CODE, API_TYPE))
            .hasSize(1);
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

    private GrantFact entryWithCondition(Long resourceEntityId, Long conditionId) {
        return new GrantFact(
            1L,            // permissionId
            10L,           // roleId
            API_TYPE,      // resourceType
            resourceEntityId,
            1L,            // grantedBits（ACCESS 位）
            false,         // scopeAll
            true,          // canGrant
            conditionId,
            true,          // hasCondition
            null,          // dependOn
            "DIRECT"       // grantSource
        );
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
}
