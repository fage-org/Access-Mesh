package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.RequestContext;
import cn.ac.fage.accessmesh.access.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.access.permission.dto.req.PermissionRecentChangesReq;
import cn.ac.fage.accessmesh.access.permission.dto.resp.ChangeLogResp;
import cn.ac.fage.accessmesh.access.permission.entity.PermissionChangeLog;
import cn.ac.fage.accessmesh.access.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.infrastructure.mapper.OperationLogMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.PermissionChangeLogMapper;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.access.permission.util.OperatorContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link LogQueryAppServiceImpl} 变更日志部分单元测试（T-PERM-032 收口 + T-PERM-033 门禁切换）。
 * <p>
 * 覆盖独立 PERMISSION_CHANGE_LOG:VIEW 门禁（审计分离，对齐 OPERATION_LOG 先例）、
 * 页面筛选维度透传（eventType 单选规整/空白归 null/受影响 user·role/时间）、
 * createdBy 暴露；recent-changes 门禁为目标实例 USER:VIEW/ROLE:VIEW（T-PERM-033 设计定案）。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class LogQueryAppServiceImplChangeLogTest {

    private static final Long TENANT = 1L;

    @Mock private PermissionChangeLogMapper changeLogMapper;
    @Mock private OperationLogMapper operationLogMapper;
    @Mock private PermQueryEngine engine;
    @Mock private TypeResolutionService typeResolutionService;

    private LogQueryAppServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LogQueryAppServiceImpl(changeLogMapper, operationLogMapper,
                engine, typeResolutionService, new ObjectMapper());
        AccessRequestContext.bind(RequestContext.user(TENANT, 100L));
    }

    @AfterEach
    void tearDown() {
        AccessRequestContext.clear();
    }

    /** T-PERM-032 审计分离：变更日志页门禁切独立 PERMISSION_CHANGE_LOG:VIEW（旧实现校验
     * SYSTEM_CONFIG:VIEW，本用例为回归锁——持 SYSTEM_CONFIG:VIEW 而无新码者被拒）。 */
    @Test
    void shouldRejectChangeLogListWithoutPermissionChangeLogView() {
        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            // 旧实现走 SYSTEM_CONFIG 校验：显式放行使旧实现越过其门禁后在断言处失败，
            // 回归锁不依赖 Mockito 严格桩行为
            org.mockito.Mockito.lenient().when(engine.hasPermissionByCode(eq(TENANT), eq(100L),
                eq(ResourceTypeCode.SYSTEM_CONFIG), isNull(), eq(OperationCodeConstants.VIEW))).thenReturn(true);
            when(engine.hasPermissionByCode(eq(TENANT), eq(100L), eq(ResourceTypeCode.PERMISSION_CHANGE_LOG),
                isNull(), eq(OperationCodeConstants.VIEW))).thenReturn(false);

            assertThrows(SecurityException.class,
                () -> service.listChangeLogs(TENANT, null, null, null, null, null, null, null, null, 0, 10));
            assertThrows(SecurityException.class,
                () -> service.countChangeLogs(TENANT, null, null, null, null, null, null, null, null));
        }
        verifyNoInteractions(changeLogMapper);
    }

    /** 筛选维度透传：eventType 单选规整为单元素集合、changeSource 空白归 null、
     * 受影响 user/role 与时间范围原样传条件组。 */
    @Test
    void shouldPassThroughPageFilters() {
        LocalDateTime since = LocalDateTime.of(2026, 8, 29, 0, 0);
        LocalDateTime until = LocalDateTime.of(2026, 8, 30, 0, 0);
        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByCode(eq(TENANT), eq(100L), eq(ResourceTypeCode.PERMISSION_CHANGE_LOG),
                isNull(), eq(OperationCodeConstants.VIEW))).thenReturn(true);
            when(changeLogMapper.countByCondition(eq(TENANT), eq("abstract_role"), eq(5L), eq(100L), eq(6L),
                eq(since), eq(until), eq(List.of("ROLE_PERMISSION_CHANGE")), eq("MANUAL")))
                .thenReturn(1L);

            long total = service.countChangeLogs(TENANT, "abstract_role", 5L,
                    "ROLE_PERMISSION_CHANGE", "MANUAL", 100L, 6L, since, until);

            assertEquals(1L, total);
        }
    }

    /** 空白筛选规整：eventType/changeSource 空串按不过滤传 null。 */
    @Test
    void shouldNormalizeBlankFiltersToNull() {
        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByCode(eq(TENANT), eq(100L), eq(ResourceTypeCode.PERMISSION_CHANGE_LOG),
                isNull(), eq(OperationCodeConstants.VIEW))).thenReturn(true);
            when(changeLogMapper.selectPageByCondition(eq(TENANT), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), isNull(), eq(0), eq(10)))
                .thenReturn(List.of());

            service.listChangeLogs(TENANT, "  ", null, " ", "", null, null, null, null, 0, 10);

            verify(changeLogMapper).selectPageByCondition(eq(TENANT), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), isNull(), eq(0), eq(10));
        }
    }

    /** T-PERM-032：ChangeLogResp 暴露操作人 createdBy（表 created_by）。 */
    @Test
    void shouldExposeCreatedByInChangeLogResp() {
        PermissionChangeLog entity = new PermissionChangeLog();
        entity.setId(9L);
        entity.setTenantId(TENANT);
        entity.setEntityType("user_role");
        entity.setOperation("INSERT");
        entity.setCreatedBy(100L);
        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByCode(eq(TENANT), eq(100L), eq(ResourceTypeCode.PERMISSION_CHANGE_LOG),
                isNull(), eq(OperationCodeConstants.VIEW))).thenReturn(true);
            when(changeLogMapper.selectPageByCondition(eq(TENANT), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), isNull(), eq(0), eq(10)))
                .thenReturn(List.of(entity));

            List<ChangeLogResp> items = service.listChangeLogs(TENANT, null, null, null, null, null, null, null, null, 0, 10);

            assertEquals(100L, items.get(0).createdBy());
        }
    }

    /** T-PERM-033 设计定案：recent-changes 门禁从 SYSTEM_CONFIG:VIEW 切目标实例 USER:VIEW
     * （回归锁——持 SYSTEM_CONFIG:VIEW 而无目标 VIEW 者被拒，旧实现放行）。 */
    @Test
    void shouldDenyRecentChangesWhenOperatorLacksTargetUserView() {
        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(typeResolutionService.resolveUserId(TENANT, "USER", "u-2")).thenReturn(1002L);
            org.mockito.Mockito.lenient().when(engine.hasPermissionByCode(eq(TENANT), eq(100L),
                eq(ResourceTypeCode.SYSTEM_CONFIG), isNull(), eq(OperationCodeConstants.VIEW))).thenReturn(true);
            when(engine.hasPermissionByCode(TENANT, 100L, ResourceTypeCode.USER, "1002", OperationCodeConstants.VIEW))
                .thenReturn(false);

            assertThrows(SecurityException.class, () -> service.getRecentChanges(TENANT,
                new PermissionRecentChangesReq("USER", "USER", "u-2", null, null, null,
                    null, null, null, 1, 20)));
        }
        verifyNoInteractions(changeLogMapper);
    }

    /** USER 未解析：不检查目标门禁，返回空（与 explain/effective-permissions USER 分支同款）。 */
    @Test
    void shouldReturnEmptyRecentChangesWhenUserUnresolved() {
        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(typeResolutionService.resolveUserId(TENANT, "USER", "u-999")).thenReturn(null);

            var resp = service.getRecentChanges(TENANT,
                new PermissionRecentChangesReq("USER", "USER", "u-999", null, null, null,
                    null, null, null, 1, 20));

            assertEquals(0, resp.total());
            assertTrue(resp.items().isEmpty());
        }
        verifyNoInteractions(changeLogMapper);
    }

    /** ROLE 未解析：类型级 ROLE:VIEW 兜底（无类型级 VIEW → 拒）。 */
    @Test
    void shouldDenyRecentChangesWhenRoleUnresolvedWithoutTypeLevelRoleView() {
        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(typeResolutionService.resolveRoleId(TENANT, "ROLE", "r-x", "admin")).thenReturn(null);
            when(engine.hasPermissionByCode(TENANT, 100L, ResourceTypeCode.ROLE, null, OperationCodeConstants.VIEW))
                .thenReturn(false);

            assertThrows(SecurityException.class, () -> service.getRecentChanges(TENANT,
                new PermissionRecentChangesReq("ROLE", null, null, "ROLE", "r-x", "admin",
                    null, null, null, 1, 20)));
        }
        verifyNoInteractions(changeLogMapper);
    }
}
