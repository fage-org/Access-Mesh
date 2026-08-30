package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.access.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.access.permission.dto.req.ConflictRuleDetectReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.ConflictRuleReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.ConflictRuleUpdateReq;
import cn.ac.fage.accessmesh.access.permission.dto.resp.ConflictDetectResp;
import cn.ac.fage.accessmesh.access.permission.dto.resp.ConflictRuleResp;
import cn.ac.fage.accessmesh.access.permission.entity.PermissionConflictRule;
import cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.access.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.permission.mapper.PermissionConflictRuleMapper;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.access.permission.util.OperatorContext;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ConflictRuleAppServiceImpl} 单元测试（T-PERM-030 建立首个用例集）。
 * <p>
 * 覆盖：类型级四档门禁（读 VIEW 三端点 + 写 CREATE/UPDATE/DELETE，2026-08-30 口径——
 * CONFLICT_RULE 无实例投影，实例级系 ID 空间错位已废弃）、detail 20020 收紧、
 * create/update 去重与 first&lt;second 规范化、update 全量覆盖（UpdateEntity 强制写列，
 * 类型切换清对侧字段）、remove 幂等跳过 + 类型级全有或全无、detect 双向匹配。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class ConflictRuleAppServiceImplTest {

    private static final long TENANT_ID = 1L;
    private static final long OPERATOR_ID = 100L;
    private static final long RULE_ID = 9L;

    @Mock private PermissionConflictRuleMapper conflictRuleMapper;
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

    private ConflictRuleAppServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ConflictRuleAppServiceImpl(conflictRuleMapper, engine);
    }

    private PermissionConflictRule newRoleRule(long id, long firstRole, long secondRole) {
        PermissionConflictRule rule = new PermissionConflictRule();
        rule.setId(id);
        rule.setTenantId(TENANT_ID);
        rule.setConflictType("ROLE_MUTEX");
        rule.setFirstAbstractRoleId(firstRole);
        rule.setSecondAbstractRoleId(secondRole);
        rule.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        rule.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        rule.setDeleteFlag(0L);
        return rule;
    }

    private PermissionConflictRule newPermRule(long id, long firstOp, long secondOp, Integer rtv) {
        PermissionConflictRule rule = new PermissionConflictRule();
        rule.setId(id);
        rule.setTenantId(TENANT_ID);
        rule.setConflictType("PERM_MUTEX");
        rule.setFirstOperationPermissionId(firstOp);
        rule.setSecondOperationPermissionId(secondOp);
        rule.setResourceTypeValue(rtv);
        rule.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        rule.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        rule.setDeleteFlag(0L);
        return rule;
    }

    private void stubTypeLevelPermission(String operationCode, boolean allowed) {
        when(engine.hasPermissionByCode(eq(TENANT_ID), eq(OPERATOR_ID), eq(ResourceTypeCode.CONFLICT_RULE),
            isNull(), eq(operationCode))).thenReturn(allowed);
    }

    @Nested
    class CreateConflictRule {

        @Test
        void shouldNormalizePair_whenRoleMutexCreatedInReverseOrder() {
            stubTypeLevelPermission(OperationCodeConstants.CREATE, true);
            when(conflictRuleMapper.selectByTenantId(TENANT_ID)).thenReturn(List.of());

            ConflictRuleReq req = new ConflictRuleReq("ROLE_MUTEX", null, null, null, 102L, 101L, "d");

            ConflictRuleResp resp = service.createConflictRule(TENANT_ID, req, OPERATOR_ID);

            ArgumentCaptor<PermissionConflictRule> captor = ArgumentCaptor.forClass(PermissionConflictRule.class);
            verify(conflictRuleMapper).insert(captor.capture());
            // first<second 规范化（对齐 schema 唯一索引注释）
            assertThat(captor.getValue().getFirstAbstractRoleId()).isEqualTo(101L);
            assertThat(captor.getValue().getSecondAbstractRoleId()).isEqualTo(102L);
            assertThat(captor.getValue().getCreatedBy()).isEqualTo(OPERATOR_ID);
            assertThat(captor.getValue().getDeleteFlag()).isZero();
            assertThat(resp.conflictType()).isEqualTo("ROLE_MUTEX");
        }

        @Test
        void shouldSetResourceTypeValue_whenPermMutexCreated() {
            stubTypeLevelPermission(OperationCodeConstants.CREATE, true);
            when(conflictRuleMapper.selectByTenantId(TENANT_ID)).thenReturn(List.of());

            ConflictRuleReq req = new ConflictRuleReq("PERM_MUTEX", 501L, 504L, 3, null, null, null);

            service.createConflictRule(TENANT_ID, req, OPERATOR_ID);

            ArgumentCaptor<PermissionConflictRule> captor = ArgumentCaptor.forClass(PermissionConflictRule.class);
            verify(conflictRuleMapper).insert(captor.capture());
            assertThat(captor.getValue().getFirstOperationPermissionId()).isEqualTo(501L);
            assertThat(captor.getValue().getSecondOperationPermissionId()).isEqualTo(504L);
            assertThat(captor.getValue().getResourceTypeValue()).isEqualTo(3);
        }

        @Test
        void shouldReject_whenConflictTypeInvalid() {
            stubTypeLevelPermission(OperationCodeConstants.CREATE, true);

            ConflictRuleReq req = new ConflictRuleReq("PRIORITY", null, null, null, 101L, 102L, null);

            assertThatThrownBy(() -> service.createConflictRule(TENANT_ID, req, OPERATOR_ID))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(PermissionErrorCode.VALIDATION_FAILED.getCode()));
            verify(conflictRuleMapper, never()).insert(any(PermissionConflictRule.class));
        }

        @Test
        void shouldReject_whenRoleMutexMissingRoleIds() {
            stubTypeLevelPermission(OperationCodeConstants.CREATE, true);

            ConflictRuleReq req = new ConflictRuleReq("ROLE_MUTEX", null, null, null, 101L, null, null);

            assertThatThrownBy(() -> service.createConflictRule(TENANT_ID, req, OPERATOR_ID))
                .isInstanceOf(BizException.class);
            verify(conflictRuleMapper, never()).insert(any(PermissionConflictRule.class));
        }

        @Test
        void shouldReject_whenPermMutexSameOperationIds() {
            // 同 id 校验先于去重查询（validateFields → isDuplicate），不触达 DB
            stubTypeLevelPermission(OperationCodeConstants.CREATE, true);

            ConflictRuleReq req = new ConflictRuleReq("PERM_MUTEX", 501L, 501L, null, null, null, null);

            assertThatThrownBy(() -> service.createConflictRule(TENANT_ID, req, OPERATOR_ID))
                .isInstanceOf(BizException.class);
            verify(conflictRuleMapper, never()).insert(any(PermissionConflictRule.class));
        }

        @Test
        void shouldRejectDuplicate_whenEquivalentRuleExistsBidirectionally() {
            stubTypeLevelPermission(OperationCodeConstants.CREATE, true);
            // 既有 (501,504,rtv=1)，请求 (504,501,rtv=1) 双向等价 → 20032
            when(conflictRuleMapper.selectByTenantId(TENANT_ID))
                .thenReturn(List.of(newPermRule(8L, 501L, 504L, 1)));

            ConflictRuleReq req = new ConflictRuleReq("PERM_MUTEX", 504L, 501L, 1, null, null, null);

            assertThatThrownBy(() -> service.createConflictRule(TENANT_ID, req, OPERATOR_ID))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(PermissionErrorCode.CONFLICT_RULE_DUPLICATE.getCode()));
            verify(conflictRuleMapper, never()).insert(any(PermissionConflictRule.class));
        }

        @Test
        void shouldTranslateUniqueViolation_toDuplicateCode() {
            stubTypeLevelPermission(OperationCodeConstants.CREATE, true);
            when(conflictRuleMapper.selectByTenantId(TENANT_ID)).thenReturn(List.of());
            when(conflictRuleMapper.insert(any(PermissionConflictRule.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException(
                    "violates unique constraint \"uk_conflict_rule_perm\""));

            ConflictRuleReq req = new ConflictRuleReq("PERM_MUTEX", 501L, 504L, null, null, null, null);

            assertThatThrownBy(() -> service.createConflictRule(TENANT_ID, req, OPERATOR_ID))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(PermissionErrorCode.CONFLICT_RULE_DUPLICATE.getCode()));
        }

        @Test
        void shouldRethrow_whenIntegrityViolationIsOtherConstraint() {
            stubTypeLevelPermission(OperationCodeConstants.CREATE, true);
            when(conflictRuleMapper.selectByTenantId(TENANT_ID)).thenReturn(List.of());
            when(conflictRuleMapper.insert(any(PermissionConflictRule.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException(
                    "violates foreign key constraint \"fk_other\""));

            ConflictRuleReq req = new ConflictRuleReq("PERM_MUTEX", 501L, 504L, null, null, null, null);

            assertThatThrownBy(() -> service.createConflictRule(TENANT_ID, req, OPERATOR_ID))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        }

        @Test
        void shouldReject_whenNoTypeLevelCreatePermission() {
            stubTypeLevelPermission(OperationCodeConstants.CREATE, false);

            ConflictRuleReq req = new ConflictRuleReq("ROLE_MUTEX", null, null, null, 101L, 102L, null);

            assertThatThrownBy(() -> service.createConflictRule(TENANT_ID, req, OPERATOR_ID))
                .isInstanceOf(SecurityException.class);
            verify(conflictRuleMapper, never()).selectByTenantId(anyLong());
            verify(conflictRuleMapper, never()).insert(any(PermissionConflictRule.class));
        }
    }

    @Nested
    class ReadEndpointsViewGate {

        @Test
        void shouldReturnDetail_withUpdatedAt_whenViewAllowed() {
            try (MockedStatic<OperatorContext> opCtx = mockStatic(OperatorContext.class)) {
                opCtx.when(OperatorContext::getOperatorId).thenReturn(OPERATOR_ID);
                stubTypeLevelPermission(OperationCodeConstants.VIEW, true);
                when(conflictRuleMapper.selectValidById(RULE_ID, TENANT_ID))
                    .thenReturn(newPermRule(RULE_ID, 501L, 504L, 1));

                ConflictRuleResp resp = service.getConflictRule(TENANT_ID, RULE_ID);

                assertThat(resp.id()).isEqualTo(RULE_ID);
                assertThat(resp.resourceTypeValue()).isEqualTo(1);
                // T-PERM-030：Resp 补 updatedAt（entity 列本就存在）
                assertThat(resp.updatedAt()).isEqualTo(LocalDateTime.of(2026, 1, 1, 0, 0));
            }
        }

        @Test
        void shouldThrow20020_whenDetailRuleMissing() {
            try (MockedStatic<OperatorContext> opCtx = mockStatic(OperatorContext.class)) {
                opCtx.when(OperatorContext::getOperatorId).thenReturn(OPERATOR_ID);
                stubTypeLevelPermission(OperationCodeConstants.VIEW, true);
                when(conflictRuleMapper.selectValidById(999L, TENANT_ID)).thenReturn(null);

                assertThatThrownBy(() -> service.getConflictRule(TENANT_ID, 999L))
                    .isInstanceOf(BizException.class)
                    .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                        .isEqualTo(PermissionErrorCode.CONFLICT_RULE_NOT_FOUND.getCode()));
            }
        }

        @Test
        void shouldRejectDetail_whenNoViewPermission() {
            try (MockedStatic<OperatorContext> opCtx = mockStatic(OperatorContext.class)) {
                opCtx.when(OperatorContext::getOperatorId).thenReturn(OPERATOR_ID);
                stubTypeLevelPermission(OperationCodeConstants.VIEW, false);

                assertThatThrownBy(() -> service.getConflictRule(TENANT_ID, RULE_ID))
                    .isInstanceOf(SecurityException.class);
                verify(conflictRuleMapper, never()).selectValidById(anyLong(), anyLong());
            }
        }

        @Test
        void shouldListAll_whenViewAllowed() {
            try (MockedStatic<OperatorContext> opCtx = mockStatic(OperatorContext.class)) {
                opCtx.when(OperatorContext::getOperatorId).thenReturn(OPERATOR_ID);
                stubTypeLevelPermission(OperationCodeConstants.VIEW, true);
                when(conflictRuleMapper.selectByTenantId(TENANT_ID))
                    .thenReturn(List.of(newRoleRule(1L, 101L, 102L), newPermRule(2L, 501L, 504L, 1)));

                List<ConflictRuleResp> list = service.listConflictRules(TENANT_ID);

                assertThat(list).hasSize(2);
                assertThat(list.get(0).firstAbstractRoleId()).isEqualTo(101L);
                assertThat(list.get(1).firstOperationPermissionId()).isEqualTo(501L);
            }
        }

        @Test
        void shouldRejectList_whenNoViewPermission() {
            try (MockedStatic<OperatorContext> opCtx = mockStatic(OperatorContext.class)) {
                opCtx.when(OperatorContext::getOperatorId).thenReturn(OPERATOR_ID);
                stubTypeLevelPermission(OperationCodeConstants.VIEW, false);

                assertThatThrownBy(() -> service.listConflictRules(TENANT_ID))
                    .isInstanceOf(SecurityException.class);
                verify(conflictRuleMapper, never()).selectByTenantId(anyLong());
            }
        }
    }

    @Nested
    class UpdateConflictRule {

        @Test
        void shouldFullOverwriteAndClearOppositeFields_whenTypeSwitchedToRoleMutex() {
            stubTypeLevelPermission(OperationCodeConstants.UPDATE, true);
            // 既有 PERM_MUTEX (501,504,rtv=1)，切换为 ROLE_MUTEX (102,101)
            when(conflictRuleMapper.selectValidById(RULE_ID, TENANT_ID))
                .thenReturn(newPermRule(RULE_ID, 501L, 504L, 1));
            when(conflictRuleMapper.selectByTenantId(TENANT_ID)).thenReturn(List.of());

            ConflictRuleUpdateReq req = new ConflictRuleUpdateReq(RULE_ID, "ROLE_MUTEX",
                null, null, null, 102L, 101L, "switched");

            service.updateConflictRule(TENANT_ID, req, OPERATOR_ID);

            ArgumentCaptor<PermissionConflictRule> captor = ArgumentCaptor.forClass(PermissionConflictRule.class);
            verify(conflictRuleMapper).update(captor.capture());
            PermissionConflictRule patch = captor.getValue();
            // 全量覆盖：ROLE 字段规范化写入，PERM 对侧字段与 rtv 强制 null（可清空）
            assertThat(patch.getConflictType()).isEqualTo("ROLE_MUTEX");
            assertThat(patch.getFirstAbstractRoleId()).isEqualTo(101L);
            assertThat(patch.getSecondAbstractRoleId()).isEqualTo(102L);
            assertThat(patch.getFirstOperationPermissionId()).isNull();
            assertThat(patch.getSecondOperationPermissionId()).isNull();
            assertThat(patch.getResourceTypeValue()).isNull();
            // T-PERM-030：审计补齐（原只写 updatedAt）
            assertThat(patch.getUpdatedBy()).isEqualTo(OPERATOR_ID);
            assertThat(patch.getUpdatedAt()).isNotNull();
            assertThat(patch.getDescription()).isEqualTo("switched");
        }

        @Test
        void shouldClearResourceTypeValue_whenPermMutexUpdatePassesNull() {
            stubTypeLevelPermission(OperationCodeConstants.UPDATE, true);
            when(conflictRuleMapper.selectValidById(RULE_ID, TENANT_ID))
                .thenReturn(newPermRule(RULE_ID, 501L, 504L, 1));
            when(conflictRuleMapper.selectByTenantId(TENANT_ID)).thenReturn(List.of());

            // rtv 显式 null=清空"全部"（PUT 语义，不与原值合并）
            ConflictRuleUpdateReq req = new ConflictRuleUpdateReq(RULE_ID, "PERM_MUTEX",
                501L, 504L, null, null, null, null);

            service.updateConflictRule(TENANT_ID, req, OPERATOR_ID);

            ArgumentCaptor<PermissionConflictRule> captor = ArgumentCaptor.forClass(PermissionConflictRule.class);
            verify(conflictRuleMapper).update(captor.capture());
            assertThat(captor.getValue().getResourceTypeValue()).isNull();
        }

        @Test
        void shouldThrow20020_whenUpdateUnknownId_andNoSideEffects() {
            // 先解析后门禁（T-PERM-028/029 模式）：未知 id 20020 优先于权限拒绝
            when(conflictRuleMapper.selectValidById(999L, TENANT_ID)).thenReturn(null);

            ConflictRuleUpdateReq req = new ConflictRuleUpdateReq(999L, "ROLE_MUTEX",
                null, null, null, 101L, 102L, null);

            assertThatThrownBy(() -> service.updateConflictRule(TENANT_ID, req, OPERATOR_ID))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(PermissionErrorCode.CONFLICT_RULE_NOT_FOUND.getCode()));
            verify(engine, never()).hasPermissionByCode(anyLong(), anyLong(), any(), any(), any());
            verify(conflictRuleMapper, never()).update(any(PermissionConflictRule.class));
        }

        @Test
        void shouldReject_whenNoTypeLevelUpdatePermission() {
            stubTypeLevelPermission(OperationCodeConstants.UPDATE, false);
            when(conflictRuleMapper.selectValidById(RULE_ID, TENANT_ID))
                .thenReturn(newRoleRule(RULE_ID, 101L, 102L));

            ConflictRuleUpdateReq req = new ConflictRuleUpdateReq(RULE_ID, "ROLE_MUTEX",
                null, null, null, 101L, 102L, null);

            assertThatThrownBy(() -> service.updateConflictRule(TENANT_ID, req, OPERATOR_ID))
                .isInstanceOf(SecurityException.class);
            verify(conflictRuleMapper, never()).update(any(PermissionConflictRule.class));
        }

        @Test
        void shouldRejectDuplicate_whenEquivalentRuleExistsExcludingSelf() {
            stubTypeLevelPermission(OperationCodeConstants.UPDATE, true);
            when(conflictRuleMapper.selectValidById(RULE_ID, TENANT_ID))
                .thenReturn(newPermRule(RULE_ID, 501L, 504L, 1));
            // 另一条等价规则（id 不同）→ 20032
            when(conflictRuleMapper.selectByTenantId(TENANT_ID))
                .thenReturn(List.of(newPermRule(8L, 504L, 501L, 1)));

            ConflictRuleUpdateReq req = new ConflictRuleUpdateReq(RULE_ID, "PERM_MUTEX",
                501L, 504L, 1, null, null, null);

            assertThatThrownBy(() -> service.updateConflictRule(TENANT_ID, req, OPERATOR_ID))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(PermissionErrorCode.CONFLICT_RULE_DUPLICATE.getCode()));
            verify(conflictRuleMapper, never()).update(any(PermissionConflictRule.class));
        }
    }

    @Nested
    class RemoveConflictRules {

        @Test
        void shouldSoftDeleteResolvedIds_whenTypeLevelDeleteAllowed() {
            when(conflictRuleMapper.selectValidByIds(eq(TENANT_ID), anySet()))
                .thenReturn(List.of(newRoleRule(RULE_ID, 101L, 102L), newPermRule(8L, 501L, 504L, 1)));
            stubTypeLevelPermission(OperationCodeConstants.DELETE, true);

            service.deleteConflictRulesByIds(TENANT_ID, List.of(RULE_ID, 8L), OPERATOR_ID);

            verify(conflictRuleMapper).softDeleteBatch(eq(TENANT_ID),
                argThat((List<Long> ids) -> ids.size() == 2 && ids.containsAll(List.of(RULE_ID, 8L))), any());
        }

        @Test
        void shouldRejectWholeBatch_whenNoTypeLevelDeletePermission() {
            when(conflictRuleMapper.selectValidByIds(eq(TENANT_ID), anySet()))
                .thenReturn(List.of(newRoleRule(RULE_ID, 101L, 102L)));
            stubTypeLevelPermission(OperationCodeConstants.DELETE, false);

            assertThatThrownBy(() -> service.deleteConflictRulesByIds(TENANT_ID, List.of(RULE_ID), OPERATOR_ID))
                .isInstanceOf(SecurityException.class);
            verify(conflictRuleMapper, never()).softDeleteBatch(anyLong(), anyList(), any());
        }

        @Test
        void shouldSkipGhostIds_andSoftDeleteResolvedOnesOnly() {
            // 幂等语义：不存在的 id 在解析阶段静默跳过，只软删解析出的实体
            when(conflictRuleMapper.selectValidByIds(eq(TENANT_ID), anySet()))
                .thenReturn(List.of(newRoleRule(RULE_ID, 101L, 102L)));
            stubTypeLevelPermission(OperationCodeConstants.DELETE, true);

            service.deleteConflictRulesByIds(TENANT_ID, List.of(RULE_ID, 999L), OPERATOR_ID);

            verify(conflictRuleMapper).softDeleteBatch(eq(TENANT_ID),
                argThat((List<Long> ids) -> ids.size() == 1 && ids.contains(RULE_ID)), any());
        }

        @Test
        void shouldMarkSkip_whenAllIdsGhost() {
            when(conflictRuleMapper.selectValidByIds(eq(TENANT_ID), anySet())).thenReturn(List.of());

            service.deleteConflictRulesByIds(TENANT_ID, List.of(999L), OPERATOR_ID);

            verify(engine, never()).hasPermissionByCode(anyLong(), anyLong(), any(), any(), any());
            verify(conflictRuleMapper, never()).softDeleteBatch(anyLong(), anyList(), any());
        }

        @Test
        void shouldMarkSkip_whenIdsNullOrEmpty() {
            service.deleteConflictRulesByIds(TENANT_ID, null, OPERATOR_ID);
            service.deleteConflictRulesByIds(TENANT_ID, List.of(), OPERATOR_ID);

            verify(conflictRuleMapper, never()).selectValidByIds(anyLong(), anySet());
            verify(conflictRuleMapper, never()).softDeleteBatch(anyLong(), anyList(), any());
        }
    }

    @Nested
    class DetectConflict {

        @Test
        void shouldReject_whenNoViewPermission() {
            try (MockedStatic<OperatorContext> opCtx = mockStatic(OperatorContext.class)) {
                opCtx.when(OperatorContext::getOperatorId).thenReturn(OPERATOR_ID);
                stubTypeLevelPermission(OperationCodeConstants.VIEW, false);

                ConflictRuleDetectReq req = new ConflictRuleDetectReq(501L, 504L, null);

                assertThatThrownBy(() -> service.detectConflictRule(TENANT_ID, req))
                    .isInstanceOf(SecurityException.class);
                verify(conflictRuleMapper, never()).selectByTenantAndResourceType(anyLong(), any());
            }
        }

        @Test
        void shouldMatchBidirectionally_includingGlobalRulesFromMapper() {
            try (MockedStatic<OperatorContext> opCtx = mockStatic(OperatorContext.class)) {
                opCtx.when(OperatorContext::getOperatorId).thenReturn(OPERATOR_ID);
                stubTypeLevelPermission(OperationCodeConstants.VIEW, true);
                // rtv=3 请求：Mapper SQL 语义保证 rtv 匹配或 IS NULL（全局）规则入选，
                // 服务层只做双向对象对匹配
                when(conflictRuleMapper.selectByTenantAndResourceType(TENANT_ID, 3))
                    .thenReturn(List.of(newPermRule(RULE_ID, 501L, 504L, null)));

                // 请求 (504,501) 反序 → 规则 (501,504) 双向命中
                ConflictDetectResp resp = service.detectConflictRule(TENANT_ID,
                    new ConflictRuleDetectReq(504L, 501L, 3));

                assertThat(resp.conflictDetected()).isTrue();
                assertThat(resp.matchedRules()).hasSize(1);
                assertThat(resp.matchedRules().get(0).id()).isEqualTo(RULE_ID);
            }
        }

        @Test
        void shouldReturnEmpty_whenNoPairMatches() {
            try (MockedStatic<OperatorContext> opCtx = mockStatic(OperatorContext.class)) {
                opCtx.when(OperatorContext::getOperatorId).thenReturn(OPERATOR_ID);
                stubTypeLevelPermission(OperationCodeConstants.VIEW, true);
                when(conflictRuleMapper.selectByTenantAndResourceType(TENANT_ID, null))
                    .thenReturn(List.of(newPermRule(RULE_ID, 501L, 504L, null)));

                ConflictDetectResp resp = service.detectConflictRule(TENANT_ID,
                    new ConflictRuleDetectReq(501L, 509L, null));

                assertThat(resp.conflictDetected()).isFalse();
                assertThat(resp.matchedRules()).isEmpty();
            }
        }
    }

    @Nested
    class DtoBeanValidation {

        private final Validator validator = validatorFactory.getValidator();

        @Test
        void shouldRejectBlankConflictType_onCreate() {
            ConflictRuleReq req = new ConflictRuleReq(" ", 501L, 504L, null, null, null, null);
            assertThat(validator.validate(req)).isNotEmpty();
        }

        @Test
        void shouldRejectDescriptionOver512_onCreateAndUpdate() {
            String tooLong = "x".repeat(513);
            assertThat(validator.validate(new ConflictRuleReq("PERM_MUTEX", 501L, 504L, null, null, null, tooLong)))
                .isNotEmpty();
            assertThat(validator.validate(new ConflictRuleUpdateReq(RULE_ID, "PERM_MUTEX", 501L, 504L, null, null, null, tooLong)))
                .isNotEmpty();
        }

        @Test
        void shouldAcceptDescriptionAt512() {
            String exact = "x".repeat(512);
            assertThat(validator.validate(new ConflictRuleReq("PERM_MUTEX", 501L, 504L, null, null, null, exact)))
                .isEmpty();
        }

        @Test
        void shouldRejectNullIdOnUpdate() {
            assertThat(validator.validate(new ConflictRuleUpdateReq(null, "ROLE_MUTEX", null, null, null, 101L, 102L, null)))
                .isNotEmpty();
        }

        @Test
        void shouldRejectNullOperationIds_onDetect() {
            assertThat(validator.validate(new ConflictRuleDetectReq(null, 504L, null))).isNotEmpty();
            assertThat(validator.validate(new ConflictRuleDetectReq(501L, null, null))).isNotEmpty();
        }
    }
}
