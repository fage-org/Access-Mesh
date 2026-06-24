package cn.ac.fage.accessmesh.permission.util;

import cn.ac.fage.accessmesh.perm.common.dto.resp.InterfaceSnapshotResp.ApiPermissionEntry;
import cn.ac.fage.accessmesh.permission.dto.query.PermResult;
import cn.ac.fage.accessmesh.permission.entity.PermissionCondition;
import cn.ac.fage.accessmesh.permission.entity.ResourceApiMapping;
import cn.ac.fage.accessmesh.permission.mapper.PermissionConditionMapper;
import cn.ac.fage.accessmesh.permission.mapper.ResourceApiMappingMapper;
import cn.ac.fage.accessmesh.permission.vo.RolePermEntry;
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
    private static final String SERVICE_CODE = "admin-service";
    private static final Integer API_TYPE = 2;
    private static final Long RESOURCE_ID = 100L;
    private static final Long CONDITION_ID = 9L;

    @Mock private ResourceApiMappingMapper apiMappingMapper;
    @Mock private PermissionConditionMapper conditionMapper;

    private SnapshotAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = new SnapshotAssembler(apiMappingMapper, conditionMapper, new ObjectMapper());
    }

    @Test
    void shouldInlineRules_whenGatewayEvaluableAndPushable() {
        String rules = "{\"logic\":\"AND\",\"items\":["
            + "{\"type\":\"IP_WHITELIST\",\"params\":{\"cidrs\":[\"10.0.0.0/8\"]}}]}";
        PermissionCondition cond = newCondition(true, rules);
        when(conditionMapper.selectValidByIds(eq(TENANT_ID), any())).thenReturn(List.of(cond));
        when(apiMappingMapper.selectForSnapshot(eq(TENANT_ID), eq(SERVICE_CODE), any()))
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
        when(apiMappingMapper.selectForSnapshot(eq(TENANT_ID), eq(SERVICE_CODE), any()))
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
        when(apiMappingMapper.selectForSnapshot(eq(TENANT_ID), eq(SERVICE_CODE), any()))
            .thenReturn(List.of(apiMapping("POST", "/api/order")));

        PermResult result = resultWith(entryWithCondition(RESOURCE_ID, CONDITION_ID));

        List<ApiPermissionEntry> entries = assembler.buildSnapshot(TENANT_ID, result, SERVICE_CODE, API_TYPE);

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).hasCondition()).isTrue();
        assertThat(entries.get(0).conditionRules()).isNull();   // 防御过滤拒绝内联
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
            "VIEW",        // operationCode
            1L,            // effectiveBits
            "DIRECT",      // grantSource
            true,          // canGrant
            conditionId,
            true,          // hasCondition
            null,          // dependOn
            false          // scopeAll
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

    private PermResult resultWith(RolePermEntry... entries) {
        // PermResult.allEntries() = scopeAllEntries + instanceEntries；测试用实例级条目
        return PermResult.builder(true, "ok")
            .instanceEntries(List.of(entries))
            .build();
    }
}
