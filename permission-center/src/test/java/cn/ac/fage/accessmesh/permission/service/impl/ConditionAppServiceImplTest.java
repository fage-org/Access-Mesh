package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.permission.cache.PermissionChangeContext;
import cn.ac.fage.accessmesh.permission.dto.req.ConditionCreateReq;
import cn.ac.fage.accessmesh.permission.dto.req.ConditionUpdateReq;
import cn.ac.fage.accessmesh.permission.entity.PermissionCondition;
import cn.ac.fage.accessmesh.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.permission.mapper.PermissionConditionMapper;
import cn.ac.fage.accessmesh.permission.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.permission.service.domain.impl.PermQueryEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ConditionAppServiceImpl} 单元测试
 * <p>
 * T-PERM-017 C2.5：聚焦 {@code gatewayEvaluable=true} 时规则类型白名单校验。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class ConditionAppServiceImplTest {

    private static final long TENANT_ID = 1L;
    private static final long OPERATOR_ID = 100L;
    private static final long CONDITION_ID = 9L;

    @Mock private PermissionConditionMapper conditionMapper;
    @Mock private RoleResourcePermissionMapper rolePermMapper;
    @Mock private PermQueryEngine engine;

    private ConditionAppServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ConditionAppServiceImpl(conditionMapper, rolePermMapper, engine, new ObjectMapper());
        // T-PERM-017 P2-A：mark 调用需绑定上下文；测试入口主动 bind，AfterEach 清理
        PermissionChangeContext.bindIfAbsent();
    }

    @AfterEach
    void tearDown() {
        PermissionChangeContext.clear();
    }

    @Nested
    class CreateConditionGatewayEvaluable {

        @Test
        void shouldAccept_whenAllItemsAreIpOrDateOrTime() {
            when(engine.hasPermission(eq(TENANT_ID), eq(OPERATOR_ID), any(), eq((Long) null), any()))
                .thenReturn(true);

            String rules = "{\"logic\":\"AND\",\"items\":["
                + "{\"type\":\"IP_WHITELIST\",\"params\":{\"cidrs\":[\"10.0.0.0/8\"]}},"
                + "{\"type\":\"DATE_RANGE\",\"params\":{\"start\":\"2026-01-01\",\"end\":\"2026-12-31\"}},"
                + "{\"type\":\"TIME_RANGE\",\"params\":{\"start\":\"09:00:00\",\"end\":\"18:00:00\"}}"
                + "]}";
            ConditionCreateReq req = new ConditionCreateReq("c-ok", "n", rules, true, true, "d");

            assertThatCode(() -> service.createCondition(TENANT_ID, req, OPERATOR_ID))
                .doesNotThrowAnyException();
        }

        @Test
        void shouldReject_whenItemTypeIsUnknown() {
            when(engine.hasPermission(eq(TENANT_ID), eq(OPERATOR_ID), any(), eq((Long) null), any()))
                .thenReturn(true);

            String rules = "{\"logic\":\"AND\",\"items\":["
                + "{\"type\":\"IP_WHITELIST\",\"params\":{\"cidrs\":[\"10.0.0.0/8\"]}},"
                + "{\"type\":\"ORG_SCOPE\",\"params\":{\"orgIds\":[1]}}"
                + "]}";
            ConditionCreateReq req = new ConditionCreateReq("c-bad", "n", rules, true, true, "d");

            assertThatThrownBy(() -> service.createCondition(TENANT_ID, req, OPERATOR_ID))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(PermissionErrorCode.CONDITION_RULES_INVALID.getCode()));
        }

        @Test
        void shouldReject_whenItemsArrayIsEmpty() {
            when(engine.hasPermission(eq(TENANT_ID), eq(OPERATOR_ID), any(), eq((Long) null), any()))
                .thenReturn(true);

            String rules = "{\"logic\":\"AND\",\"items\":[]}";
            ConditionCreateReq req = new ConditionCreateReq("c-empty", "n", rules, true, true, "d");

            assertThatThrownBy(() -> service.createCondition(TENANT_ID, req, OPERATOR_ID))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(PermissionErrorCode.CONDITION_RULES_INVALID.getCode()));
        }

        @Test
        void shouldAccept_whenGatewayEvaluableFalse_evenWithUnknownType() {
            when(engine.hasPermission(eq(TENANT_ID), eq(OPERATOR_ID), any(), eq((Long) null), any()))
                .thenReturn(true);

            // gatewayEvaluable=false 时不做白名单校验（不可下发本就不会出现在 Gateway 评估路径）
            String rules = "{\"logic\":\"AND\",\"items\":["
                + "{\"type\":\"ORG_SCOPE\",\"params\":{\"orgIds\":[1]}}"
                + "]}";
            ConditionCreateReq req = new ConditionCreateReq("c-future", "n", rules, true, false, "d");

            assertThatCode(() -> service.createCondition(TENANT_ID, req, OPERATOR_ID))
                .doesNotThrowAnyException();
        }
    }

    @Nested
    class UpdateConditionGatewayEvaluable {

        @Test
        void shouldRejectFlipToTrue_whenExistingRulesContainUnknownType() {
            // 老条件：gatewayEvaluable=false + 规则含未知类型；用户只切 flag → 不改 rules
            PermissionCondition existing = newCondition(false,
                "{\"logic\":\"AND\",\"items\":[{\"type\":\"ORG_SCOPE\",\"params\":{}}]}");
            when(engine.hasPermission(eq(TENANT_ID), eq(OPERATOR_ID), any(), eq(CONDITION_ID), any()))
                .thenReturn(true);
            when(conditionMapper.selectOneById(CONDITION_ID)).thenReturn(existing);

            ConditionUpdateReq req = new ConditionUpdateReq(CONDITION_ID, null, null, null, true, null);

            assertThatThrownBy(() -> service.updateCondition(TENANT_ID, req, OPERATOR_ID))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(PermissionErrorCode.CONDITION_RULES_INVALID.getCode()));
        }

        @Test
        void shouldAcceptFlipToTrue_whenExistingRulesArePushable() {
            PermissionCondition existing = newCondition(false,
                "{\"logic\":\"AND\",\"items\":[{\"type\":\"IP_WHITELIST\",\"params\":{\"cidrs\":[\"10.0.0.0/8\"]}}]}");
            when(engine.hasPermission(eq(TENANT_ID), eq(OPERATOR_ID), any(), eq(CONDITION_ID), any()))
                .thenReturn(true);
            when(conditionMapper.selectOneById(CONDITION_ID)).thenReturn(existing);

            ConditionUpdateReq req = new ConditionUpdateReq(CONDITION_ID, null, null, null, true, null);

            assertThatCode(() -> service.updateCondition(TENANT_ID, req, OPERATOR_ID))
                .doesNotThrowAnyException();
        }

        @Test
        void shouldRejectRulesChange_whenAlreadyTrue_andNewRulesUnknownType() {
            // 老条件：gatewayEvaluable=true；用户只改 rules 不改 flag → 用新 rules 校验
            PermissionCondition existing = newCondition(true,
                "{\"logic\":\"AND\",\"items\":[{\"type\":\"IP_WHITELIST\",\"params\":{\"cidrs\":[\"10.0.0.0/8\"]}}]}");
            when(engine.hasPermission(eq(TENANT_ID), eq(OPERATOR_ID), any(), eq(CONDITION_ID), any()))
                .thenReturn(true);
            when(conditionMapper.selectOneById(CONDITION_ID)).thenReturn(existing);

            String newRules = "{\"logic\":\"AND\",\"items\":[{\"type\":\"ORG_SCOPE\",\"params\":{}}]}";
            ConditionUpdateReq req = new ConditionUpdateReq(CONDITION_ID, null, newRules, null, null, null);

            assertThatThrownBy(() -> service.updateCondition(TENANT_ID, req, OPERATOR_ID))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(PermissionErrorCode.CONDITION_RULES_INVALID.getCode()));
        }
    }

    @Nested
    class LogicWhitelistValidation {

        @Test
        void shouldReject_whenLogicIsTypo() {
            // T-PERM-017 P2-B：'ANDD' 被旧实现按 OR 处理 → 放宽权限。写入门禁拒绝。
            when(engine.hasPermission(eq(TENANT_ID), eq(OPERATOR_ID), any(), eq((Long) null), any()))
                .thenReturn(true);

            String rules = "{\"logic\":\"ANDD\",\"items\":["
                + "{\"type\":\"IP_WHITELIST\",\"params\":{\"cidrs\":[\"10.0.0.0/8\"]}}"
                + "]}";
            ConditionCreateReq req = new ConditionCreateReq("c-typo", "n", rules, true, true, "d");

            assertThatThrownBy(() -> service.createCondition(TENANT_ID, req, OPERATOR_ID))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(PermissionErrorCode.CONDITION_RULES_INVALID.getCode()));
        }

        @Test
        void shouldReject_whenLogicIsLowerCase() {
            when(engine.hasPermission(eq(TENANT_ID), eq(OPERATOR_ID), any(), eq((Long) null), any()))
                .thenReturn(true);

            String rules = "{\"logic\":\"and\",\"items\":["
                + "{\"type\":\"IP_WHITELIST\",\"params\":{\"cidrs\":[\"10.0.0.0/8\"]}}"
                + "]}";
            ConditionCreateReq req = new ConditionCreateReq("c-lower", "n", rules, true, true, "d");

            assertThatThrownBy(() -> service.createCondition(TENANT_ID, req, OPERATOR_ID))
                .isInstanceOf(BizException.class);
        }

        @Test
        void shouldAccept_whenLogicIsExplicitOr() {
            when(engine.hasPermission(eq(TENANT_ID), eq(OPERATOR_ID), any(), eq((Long) null), any()))
                .thenReturn(true);

            String rules = "{\"logic\":\"OR\",\"items\":["
                + "{\"type\":\"IP_WHITELIST\",\"params\":{\"cidrs\":[\"10.0.0.0/8\"]}}"
                + "]}";
            ConditionCreateReq req = new ConditionCreateReq("c-or", "n", rules, true, true, "d");

            assertThatCode(() -> service.createCondition(TENANT_ID, req, OPERATOR_ID))
                .doesNotThrowAnyException();
        }

        @Test
        void shouldAccept_whenLogicOmitted_defaultsToAnd() {
            // 缺省 logic 兼容旧行为（默认 AND），写入门禁放行
            when(engine.hasPermission(eq(TENANT_ID), eq(OPERATOR_ID), any(), eq((Long) null), any()))
                .thenReturn(true);

            String rules = "{\"items\":["
                + "{\"type\":\"IP_WHITELIST\",\"params\":{\"cidrs\":[\"10.0.0.0/8\"]}}"
                + "]}";
            ConditionCreateReq req = new ConditionCreateReq("c-no-logic", "n", rules, true, true, "d");

            assertThatCode(() -> service.createCondition(TENANT_ID, req, OPERATOR_ID))
                .doesNotThrowAnyException();
        }
    }

    @Nested
    class ConditionChangeMarksServiceCodes {

        @Test
        void shouldMarkServiceCodes_whenUpdateCondition() {
            // T-PERM-017 P2-A：update 反查 serviceCodes 并 markServiceCodes
            PermissionCondition existing = newCondition(true,
                "{\"logic\":\"AND\",\"items\":[{\"type\":\"IP_WHITELIST\",\"params\":{\"cidrs\":[\"10.0.0.0/8\"]}}]}");
            when(engine.hasPermission(eq(TENANT_ID), eq(OPERATOR_ID), any(), eq(CONDITION_ID), any()))
                .thenReturn(true);
            when(conditionMapper.selectOneById(CONDITION_ID)).thenReturn(existing);
            when(rolePermMapper.selectServiceCodesByConditionIds(eq(TENANT_ID), eq(Set.of(CONDITION_ID))))
                .thenReturn(Set.of("svc-a", "svc-b"));

            ConditionUpdateReq req = new ConditionUpdateReq(CONDITION_ID, "rename", null, null, null, null);
            service.updateCondition(TENANT_ID, req, OPERATOR_ID);

            verify(rolePermMapper).selectServiceCodesByConditionIds(eq(TENANT_ID), eq(Set.of(CONDITION_ID)));
        }

        @Test
        void shouldMarkServiceCodes_whenDeleteCondition() {
            PermissionCondition existing = newCondition(true,
                "{\"logic\":\"AND\",\"items\":[{\"type\":\"DATE_RANGE\",\"params\":{\"start\":\"2026-01-01\",\"end\":\"2026-12-31\"}}]}");
            when(engine.hasPermission(eq(TENANT_ID), eq(OPERATOR_ID), any(), eq(CONDITION_ID), any()))
                .thenReturn(true);
            when(conditionMapper.selectOneById(CONDITION_ID)).thenReturn(existing);
            when(rolePermMapper.selectServiceCodesByConditionIds(eq(TENANT_ID), eq(Set.of(CONDITION_ID))))
                .thenReturn(Set.of("svc-c"));

            service.deleteCondition(TENANT_ID, CONDITION_ID, OPERATOR_ID);

            verify(rolePermMapper).selectServiceCodesByConditionIds(eq(TENANT_ID), eq(Set.of(CONDITION_ID)));
        }

        @Test
        void shouldNotMarkServiceCodes_whenNoGrantsReferenceCondition() {
            // 条件未被任何 grant 引用 → selectServiceCodesByConditionIds 返回空集合 → no-op
            PermissionCondition existing = newCondition(false,
                "{\"logic\":\"AND\",\"items\":[{\"type\":\"IP_WHITELIST\",\"params\":{\"cidrs\":[\"10.0.0.0/8\"]}}]}");
            when(engine.hasPermission(eq(TENANT_ID), eq(OPERATOR_ID), any(), eq(CONDITION_ID), any()))
                .thenReturn(true);
            when(conditionMapper.selectOneById(CONDITION_ID)).thenReturn(existing);
            when(rolePermMapper.selectServiceCodesByConditionIds(eq(TENANT_ID), eq(Set.of(CONDITION_ID))))
                .thenReturn(Set.of());

            ConditionUpdateReq req = new ConditionUpdateReq(CONDITION_ID, "rename", null, null, null, null);

            assertThatCode(() -> service.updateCondition(TENANT_ID, req, OPERATOR_ID))
                .doesNotThrowAnyException();
            verify(rolePermMapper, times(1)).selectServiceCodesByConditionIds(any(), anySet());
        }
    }

    private PermissionCondition newCondition(boolean gatewayEvaluable, String rules) {
        PermissionCondition c = new PermissionCondition();
        c.setId(CONDITION_ID);
        c.setTenantId(TENANT_ID);
        c.setCode("test");
        c.setName("test");
        c.setConditionRules(rules);
        c.setEnabled(true);
        c.setGatewayEvaluable(gatewayEvaluable);
        c.setDeleteFlag(0L);
        return c;
    }
}
