package cn.ac.fage.accessmesh.common.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class RJsonTest {
    @Test void shouldDeserializeGenericHttpEnvelope_withoutCustomClientDecoder() throws Exception {
        var json = new ObjectMapper();
        var envelope = new R<>(200,"success",List.of(new Item("large-id","ok")),"request","trace");
        var decoded = json.readValue(json.writeValueAsString(envelope),new TypeReference<R<List<Item>>>() {});
        assertThat(decoded.getCode()).isEqualTo(200);
        assertThat(decoded.getData()).containsExactly(new Item("large-id","ok"));
        assertThat(decoded.getRequestId()).isEqualTo("request");
        assertThat(decoded.getTraceId()).isEqualTo("trace");
        assertThat(json.readValue("{\"code\":20066,\"message\":\"expired\"}",R.class).getData()).isNull();
    }
    private record Item(String id,String value) {}
}
