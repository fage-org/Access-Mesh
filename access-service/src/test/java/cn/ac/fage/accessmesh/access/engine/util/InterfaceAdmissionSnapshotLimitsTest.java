package cn.ac.fage.accessmesh.access.engine.util;

import cn.ac.fage.accessmesh.access.engine.core.TypeResolutionService;
import cn.ac.fage.accessmesh.access.engine.query.EngineLimits;
import cn.ac.fage.accessmesh.access.engine.query.QueryBudgetExceededException;
import cn.ac.fage.accessmesh.access.rule.mapper.PermissionConditionMapper;
import cn.ac.fage.accessmesh.access.type.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.perm.common.dto.req.InterfaceAdmissionSnapshotReq;
import cn.ac.fage.accessmesh.perm.common.dto.resp.AdmissionRequirement;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class InterfaceAdmissionSnapshotLimitsTest {
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final InterfaceAdmissionSnapshotReq request = new InterfaceAdmissionSnapshotReq("USER", "1", "reports");
    private final LocalDateTime now = LocalDateTime.of(2026, 9, 29, 0, 0);
    private final List<InterfaceAdmissionSnapshotAssembler.RouteRequirement> routes = List.of(
        new InterfaceAdmissionSnapshotAssembler.RouteRequirement("POST", "/api/报表/查看", new AdmissionRequirement("REPORT", "VIEW")));

    @Test
    void should_bindSafeDefaultsAndRejectOversizedBodyBeforeGatewayDecoding() throws Exception {
        var source = new org.springframework.boot.context.properties.source.MapConfigurationPropertySource(java.util.Map.of());
        EngineLimits limits = new org.springframework.boot.context.properties.bind.Binder(source)
            .bindOrCreate("accessmesh.query.limits", EngineLimits.class);
        var large = java.util.stream.IntStream.range(0, 1000).mapToObj(i ->
            new InterfaceAdmissionSnapshotAssembler.RouteRequirement("POST", "/api/report/" + "r".repeat(200) + i,
                new AdmissionRequirement("REPORT", "VIEW"))).toList();
        assertThatThrownBy(() -> assembler(limits).assemble(1L, request, 1, now, now.plusSeconds(10), large, List.of()))
            .isInstanceOf(QueryBudgetExceededException.class).hasMessageContaining("SNAPSHOT_BYTES");
        var accepted = assembler(limits).assemble(1L, request, 1, now, now.plusSeconds(10), large.subList(0, 600), List.of());
        assertThat(accepted.routes()).hasSize(600);
        assertThat(json.writeValueAsBytes(cn.ac.fage.accessmesh.common.model.R.ok(accepted)).length).isLessThan(256 * 1024);
    }

    @Test
    void should_measureUtf8WireBytesAndRejectWholeSnapshotWithoutDroppingRoutes() throws Exception {
        var expected = assembler(0, 0).assemble(1L, request, 1, now, now.plusSeconds(10), routes, List.of());
        int length = json.writeValueAsBytes(expected).length;
        assertThat(assembler(1, length).assemble(1L, request, 1, now, now.plusSeconds(10), routes, List.of())).isEqualTo(expected);
        assertThatThrownBy(() -> assembler(1, length - 1).assemble(1L, request, 1, now, now.plusSeconds(10), routes, List.of()))
            .isInstanceOf(QueryBudgetExceededException.class).hasMessageContaining("SNAPSHOT_BYTES");
        assertThatThrownBy(() -> assembler(1, 0).assemble(1L, request, 1, now, now.plusSeconds(10),
            List.of(routes.getFirst(), routes.getFirst()), List.of()))
            .isInstanceOf(QueryBudgetExceededException.class).hasMessageContaining("SNAPSHOT_ROUTES");
    }

    private InterfaceAdmissionSnapshotAssembler assembler(long count, long bytes) {
        EngineLimits limits = new EngineLimits(0, 0, 0, 0, 0, 0, 0, 0, 0, count, bytes, Duration.ZERO);
        return assembler(limits);
    }

    private InterfaceAdmissionSnapshotAssembler assembler(EngineLimits limits) {
        return new InterfaceAdmissionSnapshotAssembler(mock(OperationPermissionMapper.class),
            mock(PermissionConditionMapper.class), mock(TypeResolutionService.class), json, limits);
    }
}
