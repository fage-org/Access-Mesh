package cn.ac.fage.accessmesh.access.infrastructure.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 敏感数据脱敏工具测试（T-ACCESS-007 §8.2）。
 * <p>
 * 验证 operation_log 写入前对密码/Token/密钥等敏感字段的掩码替换、
 * 非敏感字段保留、嵌套对象内敏感字段命中、以及请求体限长截断。
 * </p>
 */
class SensitiveDataUtilsTest {

    @Test
    void shouldMaskSensitiveFieldsByFieldName() {
        String masked = SensitiveDataUtils.maskJson(
            "{\"password\":\"secret123\",\"token\":\"abc\",\"name\":\"alice\"}");

        assertTrue(masked.contains("\"password\":\"***\""));
        assertTrue(masked.contains("\"token\":\"***\""));
        assertTrue(masked.contains("\"name\":\"alice\""));
        assertFalse(masked.contains("secret123"));
        assertFalse(masked.contains("abc"));
    }

    @Test
    void shouldMaskSensitiveFieldInsideNestedObject() {
        String masked = SensitiveDataUtils.maskJson(
            "{\"user\":{\"password\":\"p@ss\",\"name\":\"x\"}}");

        assertTrue(masked.contains("\"password\":\"***\""));
        assertTrue(masked.contains("\"name\":\"x\""));
        assertFalse(masked.contains("p@ss"));
    }

    @Test
    void shouldMaskCaseInsensitiveAndUnderscoreFieldNames() {
        String masked = SensitiveDataUtils.maskJson(
            "{\"new_password\":\"v\",\"APISecret\":\"k\",\"smsCode\":\"1234\",\"captchaCode\":\"5678\"}");

        assertFalse(masked.contains("v"));
        assertFalse(masked.contains("k"));
        assertFalse(masked.contains("1234"));
        assertFalse(masked.contains("5678"));
        assertTrue(masked.contains("\"new_password\":\"***\""));
        assertTrue(masked.contains("\"APISecret\":\"***\""));
        assertTrue(masked.contains("\"smsCode\":\"***\""));
        assertTrue(masked.contains("\"captchaCode\":\"***\""));
    }

    @Test
    void shouldKeepNonSensitiveValuesUntouched() {
        String masked = SensitiveDataUtils.maskJson(
            "{\"password\":\"x\",\"count\":5,\"enabled\":true,\"id\":null}");

        assertTrue(masked.contains("\"password\":\"***\""));
        assertTrue(masked.contains("\"count\":5"));
        assertTrue(masked.contains("\"enabled\":true"));
        assertTrue(masked.contains("\"id\":null"));
        assertFalse(masked.contains("\"count\":\"***\""));
    }

    @Test
    void shouldTruncateRequestBodyWithEllipsis() {
        String json = "{\"name\":\"" + "x".repeat(200) + "\"}";

        String masked = SensitiveDataUtils.maskRequestBody(json, 20);

        assertEquals(20, masked.length()); // 17 截断内容 + "..." = 20，总长不超上限（评审 P1-1：原 20+"..."=23 超列上限）
        assertTrue(masked.endsWith("..."));
        // 截断发生在 17 字符处 + 省略号（JSON 不再闭合——这正是入库限长的目的）
        assertTrue(masked.startsWith("{\"name\":\""));
        assertFalse(masked.contains("x".repeat(200)));
    }

    @Test
    void shouldMaskThenTruncateSensitiveRequestBody() {
        // 敏感字段值再长也会先被掩码，因此单敏感字段不会触发截断；用混合长非敏感字段验证截断顺序
        String json = "{\"password\":\"longvalue\",\"description\":\"" + "y".repeat(200) + "\"}";

        String masked = SensitiveDataUtils.maskRequestBody(json, 30);

        assertTrue(masked.startsWith("{\"password\":\"***\""));
        assertFalse(masked.contains("longvalue"));
        assertEquals(30, masked.length()); // 27 截断内容 + "..." = 30
        assertTrue(masked.endsWith("..."));
    }

    @Test
    void shouldUseConfiguredRequestLengthLimit() {
        // REQUEST_BODY_MAX_LEN=4000：数据库列 VARCHAR(4000)，限长不抛异常且长度可控
        String json = "{\"description\":\"" + "z".repeat(5000) + "\"}";

        String masked = SensitiveDataUtils.maskRequestBody(json, SensitiveDataUtils.REQUEST_BODY_MAX_LEN);

        assertEquals(SensitiveDataUtils.REQUEST_BODY_MAX_LEN, masked.length());
        assertTrue(masked.endsWith("..."));
    }

    @Test
    void shouldReturnNullForNullInput() {
        assertNull(SensitiveDataUtils.maskJson(null));
        assertNull(SensitiveDataUtils.maskRequestBody(null, 10));
        // 空字符串 isBlank 原样返回（不脱敏也不抛）
        assertEquals("", SensitiveDataUtils.maskJson(""));
    }

    @Test
    void shouldMaskSensitiveValuesInsideArrayElements() {
        // 数组内对象元素的敏感字段（评审 P1#3：原正则无法命中数组元素结构的敏感键值）
        String masked = SensitiveDataUtils.maskJson(
            "{\"items\":[{\"token\":\"t1\",\"name\":\"a\"},{\"apiKey\":\"k2\"}]}");

        assertFalse(masked.contains("t1"));
        assertFalse(masked.contains("k2"));
        assertTrue(masked.contains("\"name\":\"a\""));
        assertTrue(masked.contains("\"token\":\"***\""));
        assertTrue(masked.contains("\"apiKey\":\"***\""));
    }

    @Test
    void shouldMaskNestedEscapedJsonSensitiveValue() {
        // 字段值为内嵌 JSON 字符串时（如 SystemConfigReq.configValue 存 JSON），
        // 递归解析内层并脱敏敏感键值，防止内层 clientSecret 值明文入库（评审 P1#3）。
        // 说明：掩码只替换敏感键的"值"为 ***，字段名字面量（含转义形式）仍保留在输出中。
        String masked = SensitiveDataUtils.maskJson(
            "{\"configValue\":\"{\\\"clientSecret\\\":\\\"abc\\\",\\\"other\\\":1}\"}");

        // 敏感值 abc 被替换，不得明文残留
        assertFalse(masked.contains("abc"));
        // 内层 clientSecret 键掩码命中（值位置为 ***）
        assertTrue(masked.contains("clientSecret\\\":\\\"***"));
        // 非敏感字段 other 值保留
        assertTrue(masked.contains("other\\\":1"));
    }

    @Test
    void shouldMaskSensitiveNonStringValuesAsWhole() {
        // 嵌套对象内敏感字段值为对象/数组时整体替换（评审 P1#3：原 Javadoc 称非字符串原样保留，但敏感字段应整体掩码）
        String masked = SensitiveDataUtils.maskJson(
            "{\"tokens\":[{\"access\":\"a\",\"refresh\":\"b\"}],\"name\":\"keep\"}");

        assertFalse(masked.contains("access"));
        assertFalse(masked.contains("refresh"));
        assertTrue(masked.contains("\"name\":\"keep\""));
        assertTrue(masked.contains("\"tokens\":\"***\""));
    }
}
