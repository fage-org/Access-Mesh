package cn.ac.fage.accessmesh.gateway.config;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TracingConfigTest {
    @Test
    void shouldObserveEachHttpExchangeThroughTheConfiguredRegistry() {
        ObservationRegistry registry = ObservationRegistry.create();
        AtomicInteger completed = new AtomicInteger();
        registry.observationConfig().observationHandler(new ObservationHandler<Observation.Context>() {
            @Override public boolean supportsContext(Observation.Context context) { return true; }
            @Override public void onStop(Observation.Context context) { completed.incrementAndGet(); }
        });
        var builder = WebHttpHandlerBuilder.webHandler(exchange -> Mono.empty());
        new TracingConfig().gatewayObservationCustomizer(registry).customize(builder);
        builder.build().handle(MockServerHttpRequest.post("/trace-probe").build(),
                new MockServerHttpResponse()).block();
        assertThat(completed).hasValue(1);
    }
}
