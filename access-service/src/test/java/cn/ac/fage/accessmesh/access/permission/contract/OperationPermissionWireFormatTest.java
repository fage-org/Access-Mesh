package cn.ac.fage.accessmesh.access.permission.contract;

import cn.ac.fage.accessmesh.access.permission.dto.resp.OperationPermissionResp;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T-PERM-028：operation-permission 位字段线格式契约测试。
 * <p>
 * binaryBit/inheritMask 为 63 位 bigint 列，JSON number 在 &gt;2^53 丢精度——
 * 响应序列化为十进制字符串（全项目 bigint 序列化策略首例，api-contract §5.3 定稿）；
 * 请求侧 Long 组件由 Jackson 宽容接受十进制字符串。
 * </p>
 */
class OperationPermissionWireFormatTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    @DisplayName("响应 binaryBit/inheritMask 序列化为十进制字符串（含 2^62 超精度位值）")
    void shouldSerializeBitsAsDecimalStrings() throws Exception {
        OperationPermissionResp resp = new OperationPermissionResp(
            1L, 1L, "ROLE", "角色", "MANAGE", "管理",
            1L << 62, 3L, LocalDateTime.of(2026, 8, 28, 0, 0), LocalDateTime.of(2026, 8, 28, 0, 0));

        JsonNode node = objectMapper.readTree(objectMapper.writeValueAsString(resp));

        assertTrue(node.get("binaryBit").isTextual(), "binaryBit 必须是字符串");
        assertTrue(node.get("inheritMask").isTextual(), "inheritMask 必须是字符串");
        assertEquals(Long.toString(1L << 62), node.get("binaryBit").asText());
        assertEquals("3", node.get("inheritMask").asText());
    }

    @Test
    @DisplayName("请求字符串位值宽容反序列化为 Long（前端线格式统一 string）")
    void shouldDeserializeStringBitsIntoLong() throws Exception {
        String json = """
            {"resourceTypeCode":"ROLE","code":"MANAGE","name":"管理","binaryBit":"8","inheritMask":"2"}
            """;

        JsonNode node = objectMapper.readTree(json);

        assertEquals(8L, node.get("binaryBit").asLong());
        assertEquals(2L, node.get("inheritMask").asLong());
    }
}
