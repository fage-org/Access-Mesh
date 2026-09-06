package cn.ac.fage.accessmesh.common.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 响应壳线格式锁：字段集与取值不得随类名/工厂名调整而变化。
 */
class RWireShapeTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void okWireShapeIsFrozen() throws Exception {
        JsonNode node = mapper.readTree(mapper.writeValueAsString(R.ok(java.util.List.of("a"))));

        Set<String> fields = new LinkedHashSet<>();
        node.fieldNames().forEachRemaining(fields::add);
        assertEquals(Set.of("code", "message", "data", "requestId", "traceId"), fields);
        assertEquals(200, node.get("code").asInt());
        assertEquals("success", node.get("message").asText());
        assertEquals("a", node.get("data").get(0).asText());
        assertTrue(node.get("requestId").isNull());
        assertTrue(node.get("traceId").isNull());
    }

    @Test
    void failWireShapeIsFrozen() throws Exception {
        JsonNode node = mapper.readTree(mapper.writeValueAsString(R.fail(20055, "SYNC 类型禁止管理面修改")));

        assertEquals(20055, node.get("code").asInt());
        assertEquals("SYNC 类型禁止管理面修改", node.get("message").asText());
        assertTrue(node.get("data").isNull());
        assertTrue(node.get("requestId").isNull());
        assertTrue(node.get("traceId").isNull());
    }
}
