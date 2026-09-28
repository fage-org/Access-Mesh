package cn.ac.fage.accessmesh.access.engine.service.impl;

import cn.ac.fage.accessmesh.access.engine.core.TypeResolutionService;
import cn.ac.fage.accessmesh.access.engine.query.QueryExecutionEngine;
import cn.ac.fage.accessmesh.access.engine.util.InterfaceAdmissionSnapshotAssembler;
import cn.ac.fage.accessmesh.access.engine.util.InterfaceAdmissionSnapshotAssembler.RouteRequirement;
import cn.ac.fage.accessmesh.access.infrastructure.enums.AccessErrorCode;
import cn.ac.fage.accessmesh.access.resource.entity.ServiceConfig;
import cn.ac.fage.accessmesh.access.resource.mapper.ResourceApiMappingMapper;
import cn.ac.fage.accessmesh.access.resource.mapper.ServiceConfigMapper;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.common.exception.SystemException;
import cn.ac.fage.accessmesh.perm.common.dto.req.InterfaceAdmissionSnapshotReq;
import cn.ac.fage.accessmesh.perm.common.dto.resp.InterfaceAdmissionSnapshotResp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 快照构建终校验回归锁（构建期切模式/停用的代次保护完备面）。
 * <p>
 * 代次自一致比对只覆盖「首次代次读之后」的变更：入口校验与首次代次读之间切模式/
 * 停用会使代次前后一致、但服务已不适用——终校验（独立 selectAuthState 强制落库复读）
 * 必须在返回前确认仍在启用且 OPERATION_ADMISSION。旧实现（校验在重试循环外、终校验
 * 不存在）下「切 LEGACY 仍返回快照」用例因拿到快照而失败。
 * </p>
 */
class PermissionAdmissionAppServiceImplTest {

    private static final Long TENANT = 1L;
    private static final String SERVICE = "svc-a";
    private static final InterfaceAdmissionSnapshotReq REQ =
        new InterfaceAdmissionSnapshotReq("LOCAL_USER", "1", SERVICE);

    private final QueryExecutionEngine queryEngine = mock(QueryExecutionEngine.class);
    private final TypeResolutionService typeResolutionService = mock(TypeResolutionService.class);
    private final ResourceApiMappingMapper apiMappingMapper = mock(ResourceApiMappingMapper.class);
    private final ServiceConfigMapper serviceConfigMapper = mock(ServiceConfigMapper.class);
    private final InterfaceAdmissionSnapshotAssembler snapshotAssembler = mock(InterfaceAdmissionSnapshotAssembler.class);
    private final PermissionAdmissionAppServiceImpl admission = new PermissionAdmissionAppServiceImpl(
        queryEngine, typeResolutionService, apiMappingMapper, serviceConfigMapper, snapshotAssembler);

    @BeforeEach
    void setUp() {
        when(serviceConfigMapper.selectByTenantAndServiceCode(TENANT, SERVICE))
            .thenReturn(enabledConfig("OPERATION_ADMISSION"));
        when(typeResolutionService.resolveUserId(TENANT, REQ.subjectTypeCode(), REQ.subjectExternalId()))
            .thenReturn(1L);
        when(apiMappingMapper.selectEnabledByServiceCode(TENANT, SERVICE)).thenReturn(List.of());
        when(snapshotAssembler.resolveRouteRequirements(anyLong(), anyList())).thenReturn(List.of());
        when(snapshotAssembler.assemble(anyLong(), any(InterfaceAdmissionSnapshotReq.class), anyLong(),
            any(LocalDateTime.class), any(LocalDateTime.class), anyList(), anyList()))
            .thenReturn(mock(InterfaceAdmissionSnapshotResp.class));
    }

    private static ServiceConfig enabledConfig(String mode) {
        ServiceConfig config = new ServiceConfig();
        config.setStatus(1);
        config.setApiAuthMode(mode);
        config.setConfigGeneration(5L);
        return config;
    }

    /** 捕获 assemble 的 routes/candidates 参数（断言空快照语义）。 */
    @SuppressWarnings("unchecked")
    private List<Object>[] captureAssembleCalls() {
        ArgumentCaptor<List<RouteRequirement>> routesCaptor =
            ArgumentCaptor.forClass((Class) List.class);
        ArgumentCaptor<List<InterfaceAdmissionSnapshotResp.OperationCandidateEntry>> candidatesCaptor =
            ArgumentCaptor.forClass((Class) List.class);
        verify(snapshotAssembler).assemble(anyLong(), any(InterfaceAdmissionSnapshotReq.class), anyLong(),
            any(LocalDateTime.class), any(LocalDateTime.class), routesCaptor.capture(), candidatesCaptor.capture());
        return new List[]{routesCaptor.getValue(), candidatesCaptor.getValue()};
    }

    @Test
    @DisplayName("稳定且终校验通过 → 正常组装快照")
    void stableAndAuthStateHolds_returnsSnapshot() {
        when(serviceConfigMapper.selectConfigGeneration(TENANT, SERVICE)).thenReturn(5L);
        when(serviceConfigMapper.selectAuthState(TENANT, SERVICE))
            .thenReturn(enabledConfig("OPERATION_ADMISSION"));

        admission.interfaceAdmissionSnapshot(TENANT, REQ);

        verify(snapshotAssembler).assemble(org.mockito.ArgumentMatchers.eq(TENANT),
            org.mockito.ArgumentMatchers.eq(REQ), org.mockito.ArgumentMatchers.eq(5L),
            any(LocalDateTime.class), any(LocalDateTime.class), anyList(), anyList());
    }

    @Test
    @DisplayName("构建后代次稳定但已切 LEGACY → 终校验 20071，不返回快照（重试不沿用入口旧状态）")
    void modeSwitchedBeforeGenerationRead_finalCheckRejects() {
        // 场景：入口校验时 OPERATION_ADMISSION、切 LEGACY 发生在入口校验与首次代次读之间——
        // 代次前后一致（5L==5L），自一致比对无法发现，只有终校验能拦
        when(serviceConfigMapper.selectConfigGeneration(TENANT, SERVICE)).thenReturn(5L);
        when(serviceConfigMapper.selectAuthState(TENANT, SERVICE))
            .thenReturn(enabledConfig("LEGACY_API"));

        assertThatThrownBy(() -> admission.interfaceAdmissionSnapshot(TENANT, REQ))
            .isInstanceOf(BizException.class)
            .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                .isEqualTo(AccessErrorCode.ADMISSION_CONFIG_FAULT.getCode()));
        verify(snapshotAssembler, never()).assemble(anyLong(), any(InterfaceAdmissionSnapshotReq.class), anyLong(),
            any(), any(), anyList(), anyList());
    }

    @Test
    @DisplayName("构建后代次稳定但已停用 → 终校验按停用语义返回空路由快照（与入口停用同口径）")
    void disabledBeforeGenerationRead_finalCheckReturnsEmptySnapshot() {
        when(serviceConfigMapper.selectConfigGeneration(TENANT, SERVICE)).thenReturn(5L);
        ServiceConfig disabled = enabledConfig("OPERATION_ADMISSION");
        disabled.setStatus(0);
        when(serviceConfigMapper.selectAuthState(TENANT, SERVICE)).thenReturn(disabled);

        admission.interfaceAdmissionSnapshot(TENANT, REQ);

        List<Object>[] captured = captureAssembleCalls();
        assertThat(captured[0]).isEmpty();
        assertThat(captured[1]).isEmpty();
    }

    @Test
    @DisplayName("重试轮发现代次改变 → 重建；每轮都以终校验收口（重试不沿用入口旧状态）")
    void generationChangeTriggersRetry_thenFinalCheckGoverns() {
        when(serviceConfigMapper.selectConfigGeneration(TENANT, SERVICE)).thenReturn(5L, 6L, 6L, 6L);
        // 第一轮：代次 5→6 重建；第二轮：代次 6 稳定但已停用 → 空快照
        ServiceConfig disabled = enabledConfig("OPERATION_ADMISSION");
        disabled.setStatus(0);
        when(serviceConfigMapper.selectAuthState(TENANT, SERVICE)).thenReturn(disabled);

        admission.interfaceAdmissionSnapshot(TENANT, REQ);

        // 两轮各读两次代次；第二轮终校验决定空快照（不返回第一轮基于旧代次的结果）
        verify(serviceConfigMapper, times(4)).selectConfigGeneration(TENANT, SERVICE);
        List<Object>[] captured = captureAssembleCalls();
        assertThat(captured[0]).isEmpty();
        assertThat(captured[1]).isEmpty();
    }

    @Test
    @DisplayName("代次持续变更达重试上限 → 技术故障失败关闭")
    void generationNeverStable_failsClosed() {
        when(serviceConfigMapper.selectConfigGeneration(TENANT, SERVICE)).thenReturn(1L, 2L, 3L, 4L, 5L, 6L, 7L);

        assertThatThrownBy(() -> admission.interfaceAdmissionSnapshot(TENANT, REQ))
            .isInstanceOf(SystemException.class);
    }
}
