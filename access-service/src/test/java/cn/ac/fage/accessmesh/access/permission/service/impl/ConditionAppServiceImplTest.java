package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.access.infrastructure.PermissionChangeContext;
import cn.ac.fage.accessmesh.access.permission.dto.req.ConditionCreateReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.ConditionDetailReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.ConditionRemoveReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.ConditionUpdateReq;
import cn.ac.fage.accessmesh.access.permission.dto.resp.ConditionResp;
import cn.ac.fage.accessmesh.access.permission.entity.PermissionCondition;
import cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.access.permission.mapper.PermissionConditionMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.argThat;
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
 * T-PERM-029：管理端点业务键 code 切换回归锁（detail 20006 / update 定位 / remove 按编码批量）；
 * 写门禁为类型级（CONDITION 无实例投影，2026-08-30 口径收窄，实例投影登记 T-PERM-048）。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class ConditionAppServiceImplTest {

    private static final long TENANT_ID = 1L;
    private static final long OPERATOR_ID = 100L;
    private static final long CONDITION_ID = 9L;
    private static final String CONDITION_CODE = "test";

    @Mock private PermissionConditionMapper conditionMapper;
    @Mock private RoleResourcePermissionMapper rolePermMapper;
    @Mock private PermQueryEngine engine;

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
            when(engine.hasPermissionByCode(eq(TENANT_ID), eq(OPERATOR_ID), any(), eq((String) null), any()))
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
            when(engine.hasPermissionByCode(eq(TENANT_ID), eq(OPERATOR_ID), any(), eq((String) null), any()))
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
            when(engine.hasPermissionByCode(eq(TENANT_ID), eq(OPERATOR_ID), any(), eq((String) null), any()))
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
            when(engine.hasPermissionByCode(eq(TENANT_ID), eq(OPERATOR_ID), any(), eq((String) null), any()))
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
            when(conditionMapper.selectValidByCode(TENANT_ID, CONDITION_CODE)).thenReturn(existing);
            when(engine.hasPermissionByCode(eq(TENANT_ID), eq(OPERATOR_ID), any(), eq((String) null), any()))
                .thenReturn(true);

            ConditionUpdateReq req = new ConditionUpdateReq(CONDITION_CODE, null, null, null, true, null);

            assertThatThrownBy(() -> service.updateCondition(TENANT_ID, req, OPERATOR_ID))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(PermissionErrorCode.CONDITION_RULES_INVALID.getCode()));
        }

        @Test
        void shouldAcceptFlipToTrue_whenExistingRulesArePushable() {
            PermissionCondition existing = newCondition(false,
                "{\"logic\":\"AND\",\"items\":[{\"type\":\"IP_WHITELIST\",\"params\":{\"cidrs\":[\"10.0.0.0/8\"]}}]}");
            when(conditionMapper.selectValidByCode(TENANT_ID, CONDITION_CODE)).thenReturn(existing);
            when(engine.hasPermissionByCode(eq(TENANT_ID), eq(OPERATOR_ID), any(), eq((String) null), any()))
                .thenReturn(true);

            ConditionUpdateReq req = new ConditionUpdateReq(CONDITION_CODE, null, null, null, true, null);

            assertThatCode(() -> service.updateCondition(TENANT_ID, req, OPERATOR_ID))
                .doesNotThrowAnyException();
        }

        @Test
        void shouldRejectRulesChange_whenAlreadyTrue_andNewRulesUnknownType() {
            // 老条件：gatewayEvaluable=true；用户只改 rules 不改 flag → 用新 rules 校验
            PermissionCondition existing = newCondition(true,
                "{\"logic\":\"AND\",\"items\":[{\"type\":\"IP_WHITELIST\",\"params\":{\"cidrs\":[\"10.0.0.0/8\"]}}]}");
            when(conditionMapper.selectValidByCode(TENANT_ID, CONDITION_CODE)).thenReturn(existing);
            when(engine.hasPermissionByCode(eq(TENANT_ID), eq(OPERATOR_ID), any(), eq((String) null), any()))
                .thenReturn(true);

            String newRules = "{\"logic\":\"AND\",\"items\":[{\"type\":\"ORG_SCOPE\",\"params\":{}}]}";
            ConditionUpdateReq req = new ConditionUpdateReq(CONDITION_CODE, null, newRules, null, null, null);

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
            when(engine.hasPermissionByCode(eq(TENANT_ID), eq(OPERATOR_ID), any(), eq((String) null), any()))
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
        void shouldReject_whenLogicIsBlankString() {
            // T-PERM-017 P2-B：显式空串旧实现写入放行但运行时走 OR，现改为写入门禁拒绝。
            when(engine.hasPermissionByCode(eq(TENANT_ID), eq(OPERATOR_ID), any(), eq((String) null), any()))
                .thenReturn(true);

            String rules = "{\"logic\":\"\",\"items\":["
                + "{\"type\":\"IP_WHITELIST\",\"params\":{\"cidrs\":[\"10.0.0.0/8\"]}}"
                + "]}";
            ConditionCreateReq req = new ConditionCreateReq("c-blank", "n", rules, true, true, "d");

            assertThatThrownBy(() -> service.createCondition(TENANT_ID, req, OPERATOR_ID))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(PermissionErrorCode.CONDITION_RULES_INVALID.getCode()));
        }

        @Test
        void shouldReject_whenLogicIsExplicitNull() {
            // T-PERM-017 P3：显式 null 会在运行时 fail-close；写入门禁保持一致，直接拒绝。
            when(engine.hasPermissionByCode(eq(TENANT_ID), eq(OPERATOR_ID), any(), eq((String) null), any()))
                .thenReturn(true);

            String rules = "{\"logic\":null,\"items\":["
                + "{\"type\":\"IP_WHITELIST\",\"params\":{\"cidrs\":[\"10.0.0.0/8\"]}}"
                + "]}";
            ConditionCreateReq req = new ConditionCreateReq("c-null", "n", rules, true, true, "d");

            assertThatThrownBy(() -> service.createCondition(TENANT_ID, req, OPERATOR_ID))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(PermissionErrorCode.CONDITION_RULES_INVALID.getCode()));
        }

        @Test
        void shouldReject_whenLogicIsLowerCase() {
            when(engine.hasPermissionByCode(eq(TENANT_ID), eq(OPERATOR_ID), any(), eq((String) null), any()))
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
            when(engine.hasPermissionByCode(eq(TENANT_ID), eq(OPERATOR_ID), any(), eq((String) null), any()))
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
            when(engine.hasPermissionByCode(eq(TENANT_ID), eq(OPERATOR_ID), any(), eq((String) null), any()))
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
            when(conditionMapper.selectValidByCode(TENANT_ID, CONDITION_CODE)).thenReturn(existing);
            when(engine.hasPermissionByCode(eq(TENANT_ID), eq(OPERATOR_ID), any(), eq((String) null), any()))
                .thenReturn(true);
            when(rolePermMapper.selectServiceCodesByConditionIds(eq(TENANT_ID), eq(Set.of(CONDITION_ID))))
                .thenReturn(Set.of("svc-a", "svc-b"));

            ConditionUpdateReq req = new ConditionUpdateReq(CONDITION_CODE, "rename", null, null, null, null);
            service.updateCondition(TENANT_ID, req, OPERATOR_ID);

            verify(rolePermMapper).selectServiceCodesByConditionIds(eq(TENANT_ID), eq(Set.of(CONDITION_ID)));
        }

        @Test
        void shouldMarkServiceCodes_whenDeleteConditionsByCodes() {
            // 原 shouldMarkServiceCodes_whenDeleteCondition（单删孤儿方法已随 T-PERM-029 删除），覆盖迁移批量版
            PermissionCondition existing = newCondition(true,
                "{\"logic\":\"AND\",\"items\":[{\"type\":\"DATE_RANGE\",\"params\":{\"start\":\"2026-01-01\",\"end\":\"2026-12-31\"}}]}");
            when(conditionMapper.selectValidByCodes(eq(TENANT_ID), anySet())).thenReturn(List.of(existing));
            when(engine.hasPermissionByCode(eq(TENANT_ID), eq(OPERATOR_ID), any(), eq((String) null), any()))
                .thenReturn(true);
            when(rolePermMapper.selectServiceCodesByConditionIds(eq(TENANT_ID), eq(Set.of(CONDITION_ID))))
                .thenReturn(Set.of("svc-c"));

            service.deleteConditionsByCodes(TENANT_ID, List.of(CONDITION_CODE), OPERATOR_ID);

            verify(conditionMapper).softDeleteBatch(eq(TENANT_ID), eq(List.of(CONDITION_ID)), any());
            verify(rolePermMapper).selectServiceCodesByConditionIds(eq(TENANT_ID), eq(Set.of(CONDITION_ID)));
        }

        @Test
        void shouldNotMarkServiceCodes_whenNoGrantsReferenceCondition() {
            // 条件未被任何 grant 引用 → selectServiceCodesByConditionIds 返回空集合 → no-op
            PermissionCondition existing = newCondition(false,
                "{\"logic\":\"AND\",\"items\":[{\"type\":\"IP_WHITELIST\",\"params\":{\"cidrs\":[\"10.0.0.0/8\"]}}]}");
            when(conditionMapper.selectValidByCode(TENANT_ID, CONDITION_CODE)).thenReturn(existing);
            when(engine.hasPermissionByCode(eq(TENANT_ID), eq(OPERATOR_ID), any(), eq((String) null), any()))
                .thenReturn(true);
            when(rolePermMapper.selectServiceCodesByConditionIds(eq(TENANT_ID), eq(Set.of(CONDITION_ID))))
                .thenReturn(Set.of());

            ConditionUpdateReq req = new ConditionUpdateReq(CONDITION_CODE, "rename", null, null, null, null);

            assertThatCode(() -> service.updateCondition(TENANT_ID, req, OPERATOR_ID))
                .doesNotThrowAnyException();
            verify(rolePermMapper, times(1)).selectServiceCodesByConditionIds(any(), anySet());
        }
    }

    @Nested
    class BusinessKeyEndpoints {

        @Test
        void shouldReturnConditionWithUpdatedAt_whenGetByCode() {
            // T-PERM-029：detail 按业务键 code 命中，Resp 透出 updatedAt
            PermissionCondition existing = newCondition(true,
                "{\"logic\":\"AND\",\"items\":[{\"type\":\"IP_WHITELIST\",\"params\":{\"cidrs\":[\"10.0.0.0/8\"]}}]}");
            when(conditionMapper.selectValidByCode(TENANT_ID, CONDITION_CODE)).thenReturn(existing);

            ConditionResp resp = service.getCondition(TENANT_ID, CONDITION_CODE);

            assertThat(resp.code()).isEqualTo(CONDITION_CODE);
            assertThat(resp.id()).isEqualTo(CONDITION_ID);
            assertThat(resp.updatedAt()).isEqualTo(existing.getUpdatedAt());
        }

        @Test
        void shouldThrow20006_whenGetByUnknownCode() {
            // T-PERM-029：原 data=null 宽松语义删除，查不到抛 20006（对齐 resource-entity/detail 收紧定案）
            when(conditionMapper.selectValidByCode(TENANT_ID, "ghost")).thenReturn(null);

            assertThatThrownBy(() -> service.getCondition(TENANT_ID, "ghost"))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(PermissionErrorCode.CONDITION_NOT_FOUND.getCode()));
        }

        @Test
        void shouldThrow20006_whenUpdateUnknownCode_andNoSideEffects() {
            // T-PERM-029：update 按业务键定位，未知 code 20006 且零副作用（不触达门禁/不写库）
            when(conditionMapper.selectValidByCode(TENANT_ID, "ghost")).thenReturn(null);

            ConditionUpdateReq req = new ConditionUpdateReq("ghost", "rename", null, null, null, null);

            assertThatThrownBy(() -> service.updateCondition(TENANT_ID, req, OPERATOR_ID))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(PermissionErrorCode.CONDITION_NOT_FOUND.getCode()));
            verify(engine, never()).hasPermissionByCode(anyLong(), anyLong(), any(), any(), any());
            verify(conditionMapper, never()).update(any(PermissionCondition.class));
        }

        @Test
        void shouldSkipUnknownCodes_andSoftDeleteResolvedOnesOnly() {
            // T-PERM-029：remove 按编码批量，幽灵编码静默跳过（幂等语义，对齐 resource-entity/remove）；
            // 门禁为类型级（CONDITION 无实例投影，2026-08-30 口径收窄），与解析实体数无关
            PermissionCondition existing = newCondition(true,
                "{\"logic\":\"AND\",\"items\":[{\"type\":\"IP_WHITELIST\",\"params\":{\"cidrs\":[\"10.0.0.0/8\"]}}]}");
            when(conditionMapper.selectValidByCodes(eq(TENANT_ID), argThat((Set<String> codes) -> codes.containsAll(List.of(CONDITION_CODE, "ghost")))))
                .thenReturn(List.of(existing));
            when(engine.hasPermissionByCode(eq(TENANT_ID), eq(OPERATOR_ID), any(), eq((String) null), any()))
                .thenReturn(true);

            service.deleteConditionsByCodes(TENANT_ID, List.of(CONDITION_CODE, "ghost"), OPERATOR_ID);

            verify(conditionMapper).softDeleteBatch(eq(TENANT_ID),
                argThat((List<Long> ids) -> ids.size() == 1 && ids.contains(CONDITION_ID)), any());
        }

        @Test
        void shouldRejectWholeBatch_whenNoTypeLevelDeletePermission() {
            // 类型级全有或全无：无 CONDITION:DELETE 类型级授权 → 整批抛 SecurityException，零删除
            PermissionCondition existing = newCondition(true,
                "{\"logic\":\"AND\",\"items\":[{\"type\":\"IP_WHITELIST\",\"params\":{\"cidrs\":[\"10.0.0.0/8\"]}}]}");
            when(conditionMapper.selectValidByCodes(eq(TENANT_ID), anySet())).thenReturn(List.of(existing));
            when(engine.hasPermissionByCode(eq(TENANT_ID), eq(OPERATOR_ID), any(), eq((String) null), any()))
                .thenReturn(false);

            assertThatThrownBy(() -> service.deleteConditionsByCodes(TENANT_ID, List.of(CONDITION_CODE), OPERATOR_ID))
                .isInstanceOf(SecurityException.class);
            verify(conditionMapper, never()).softDeleteBatch(anyLong(), anyList(), any());
            verify(rolePermMapper, never()).selectServiceCodesByConditionIds(anyLong(), anySet());
        }

        @Test
        void shouldSkipAll_whenCodesAreBlankOnly() {
            // 全空白编码清洗后为空 → markSkip 返回，不触达任何查询
            service.deleteConditionsByCodes(TENANT_ID, List.of(" ", ""), OPERATOR_ID);

            verify(conditionMapper, never()).selectValidByCodes(anyLong(), anySet());
            verify(conditionMapper, never()).softDeleteBatch(anyLong(), anyList(), any());
        }
    }

    @Nested
    class DtoBeanValidation {

        // codex 外部评审 P3：本批新增的 @Size 列宽与 remove 元素级校验须有 Bean Validation 层锁定
        //（HTTP 层 @Valid 触发 400；先例 DomainConfigAppServiceImplTest 白名单校验用例）
        @Test
        void shouldEnforceLengthAndElementConstraints() {
            Validator validator = validatorFactory.getValidator();

            // 合法形态：四个请求 DTO 全部通过
            assertTrue(validator.validate(new ConditionCreateReq("c-1", "n", "{}", true, false, "d")).isEmpty());
            assertTrue(validator.validate(new ConditionUpdateReq("c-1", "n", "{}", true, false, "d")).isEmpty());
            assertTrue(validator.validate(new ConditionRemoveReq(List.of("c-1", "c-2"))).isEmpty());
            assertTrue(validator.validate(new ConditionDetailReq("c-1")).isEmpty());

            String over64 = "c".repeat(65);
            String over128 = "n".repeat(129);
            String over512 = "d".repeat(513);

            // code 列宽/空白（create/update/detail 三 DTO + create 必填）
            assertFalse(validator.validate(new ConditionCreateReq(over64, "n", "{}", null, null, null)).isEmpty());
            assertFalse(validator.validate(new ConditionCreateReq(" ", "n", "{}", null, null, null)).isEmpty());
            assertFalse(validator.validate(new ConditionUpdateReq(over64, null, null, null, null, null)).isEmpty());
            assertFalse(validator.validate(new ConditionUpdateReq(" ", null, null, null, null, null)).isEmpty());
            assertFalse(validator.validate(new ConditionDetailReq(over64)).isEmpty());

            // name/description 列宽（本批评审补齐，超长应在 400 而非 DB 500）
            assertFalse(validator.validate(new ConditionCreateReq("c-1", over128, "{}", null, null, null)).isEmpty());
            assertFalse(validator.validate(new ConditionCreateReq("c-1", "n", "{}", null, null, over512)).isEmpty());
            assertFalse(validator.validate(new ConditionUpdateReq("c-1", over128, null, null, null, null)).isEmpty());
            assertFalse(validator.validate(new ConditionUpdateReq("c-1", null, null, null, null, over512)).isEmpty());

            // remove 元素级：空列表/空白元素/超长元素整批拒绝
            assertFalse(validator.validate(new ConditionRemoveReq(List.of())).isEmpty());
            assertFalse(validator.validate(new ConditionRemoveReq(List.of(" ", "c-1"))).isEmpty());
            assertFalse(validator.validate(new ConditionRemoveReq(List.of(over64))).isEmpty());
        }
    }

    private PermissionCondition newCondition(boolean gatewayEvaluable, String rules) {
        PermissionCondition c = new PermissionCondition();
        c.setId(CONDITION_ID);
        c.setTenantId(TENANT_ID);
        c.setCode(CONDITION_CODE);
        c.setName("test");
        c.setConditionRules(rules);
        c.setEnabled(true);
        c.setGatewayEvaluable(gatewayEvaluable);
        c.setDeleteFlag(0L);
        c.setUpdatedAt(LocalDateTime.of(2026, 8, 30, 12, 0));
        return c;
    }
}
