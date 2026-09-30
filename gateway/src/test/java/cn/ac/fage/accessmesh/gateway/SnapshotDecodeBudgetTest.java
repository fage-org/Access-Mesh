package cn.ac.fage.accessmesh.gateway;

import cn.ac.fage.accessmesh.common.model.R;
import cn.ac.fage.accessmesh.gateway.config.GatewayProperties;
import cn.ac.fage.accessmesh.gateway.config.WebClientConfig;
import cn.ac.fage.accessmesh.gateway.service.PermissionClient;
import cn.ac.fage.accessmesh.perm.common.dto.resp.AdmissionRequirement;
import cn.ac.fage.accessmesh.perm.common.dto.resp.InterfaceAdmissionSnapshotResp;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SnapshotDecodeBudgetTest {
    @Test
    void should_rejectSnapshotAboveClientDecodeBudget_overRealHttp() throws Exception {
        var routes = IntStream.range(0, 1000).mapToObj(i -> new InterfaceAdmissionSnapshotResp.RouteEntry(
            "POST", "/api/report/" + "r".repeat(200) + i, new AdmissionRequirement("REPORT", "VIEW"))).toList();
        var now = LocalDateTime.of(2026, 9, 29, 0, 0);
        var snapshot = new InterfaceAdmissionSnapshotResp(InterfaceAdmissionSnapshotResp.CURRENT_SCHEMA_VERSION,
            1L, new InterfaceAdmissionSnapshotResp.Subject("USER", "1"), "reports", now, now.plusSeconds(10),
            1, routes, List.of(), "OPERATION_ADMISSION", true);
        byte[] body = new ObjectMapper().findAndRegisterModules().writeValueAsBytes(R.ok(snapshot));
        assertThat(body.length).isGreaterThan(256 * 1024);
        Files.writeString(Path.of("target", "t-perm-093-snapshot-wire-size.txt"), Integer.toString(body.length));
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            GatewayProperties properties = new GatewayProperties();
            properties.getPermission().setServiceUrl("http://127.0.0.1:" + server.getAddress().getPort());
            PermissionClient client = new PermissionClient(new WebClientConfig().loadBalancedWebClientBuilder(), properties);
            assertThatThrownBy(() -> client.interfaceAdmissionSnapshot("USER", 1L, "reports", 1L).block(Duration.ofSeconds(5)))
                .hasStackTraceContaining("Exceeded limit on max bytes to buffer");
        } finally { server.stop(0); }
    }
}
