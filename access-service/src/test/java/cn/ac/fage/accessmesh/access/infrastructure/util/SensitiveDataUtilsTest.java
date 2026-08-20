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

    @Test
    void shouldMaskPiiFieldNamesLikePhoneAndEmail() {
        // PII（手机号/邮箱）不落入审计明文（评审 P1#3 扩词表）
        String masked = SensitiveDataUtils.maskJson(
            "{\"phone\":\"13812345678\",\"email\":\"alice@example.com\",\"name\":\"keep\"}");

        assertFalse(masked.contains("13812345678"));
        assertFalse(masked.contains("alice@example.com"));
        assertTrue(masked.contains("\"name\":\"keep\""));
        assertTrue(masked.contains("\"phone\":\"***\""));
        assertTrue(masked.contains("\"email\":\"***\""));
    }

    @Test
    void shouldMaskIdCardAndPrivateKeyFieldNames() {
        // 身份证/私人证书密钥脱敏（评审 P1#3 扩词表）
        String masked = SensitiveDataUtils.maskJson(
            "{\"idCard\":\"110101199001011234\",\"privateKey\":\"MIIEvQ...\",\"count\":5}");

        assertFalse(masked.contains("110101199001011234"));
        assertFalse(masked.contains("MIIEvQ"));
        assertTrue(masked.contains("\"idCard\":\"***\""));
        assertTrue(masked.contains("\"privateKey\":\"***\""));
        assertTrue(masked.contains("\"count\":5"));
    }

    @Test
    void shouldNotMaskBusinessCodeByDefaultButMaskCodeVerifier() {
        // P2#3 收窄：`code`（OAuth2 授权码）不再全局精确掩码——同名业务字段
        // （OrgUpdateReq.code 组织编码 / ResourceUpdateReq.code 资源编码）会被误掩码降低审计价值。
        // 默认只精确掩码 `codeVerifier`（PKCE 验证器，全局唯一无业务碰撞），且不得误伤
        // serviceCode/roleCode/resourceCode 等 contains 命中的合法业务字段。
        String masked = SensitiveDataUtils.maskJson(
            "{\"code\":\"org-code-001\",\"codeVerifier\":\"pkce-verifier\",\"code_verifier\":\"p2\","
                + "\"serviceCode\":\"svc\",\"roleCode\":\"admin\",\"resourceCode\":\"r1\",\"name\":\"keep\"}");

        // 业务 code 保留原样（不再整体 ***）
        assertTrue(masked.contains("\"code\":\"org-code-001\""));
        assertFalse(masked.contains("\"code\":\"***\""));
        // codeVerifier（及下划线归一）仍精确掩码
        assertFalse(masked.contains("pkce-verifier"));
        assertFalse(masked.contains("\"p2\""));
        assertTrue(masked.contains("\"codeVerifier\":\"***\""));
        assertTrue(masked.contains("\"code_verifier\":\"***\""));
        // contains 命中非精确业务字段保留
        assertTrue(masked.contains("\"serviceCode\":\"svc\""));
        assertTrue(masked.contains("\"roleCode\":\"admin\""));
        assertTrue(masked.contains("\"resourceCode\":\"r1\""));
        assertTrue(masked.contains("\"name\":\"keep\""));
    }

    @Test
    void shouldMaskCodeWhenScopedAsExtraPreciseField() {
        // P2#3：OAuth2 token/refresh 场景经 OperationLogRuntimeContext.markSensitiveField("code")
        // 把 code 并入调用作用域精确匹配集合，授权码按那是掩码、业务场景不受影响。
        String masked = SensitiveDataUtils.maskJson(
            "{\"code\":\"authcode123\",\"name\":\"keep\"}", java.util.Set.of("code"));

        assertFalse(masked.contains("authcode123"));
        assertTrue(masked.contains("\"code\":\"***\""));
        assertTrue(masked.contains("\"name\":\"keep\""));
    }

    @Test
    void shouldMaskConfigValueWhenConfigKeyIsSecretClass() {
        // P1#1：当请求体同时含 configKey 与 configValue、且配置键命中密钥类
        // （password/secret/token/apikey/privatekey）时，跨字段掩码整个 configValue，
        // 覆盖其纯文本/标量 JSON 形态（字段名 configValue 本身不含敏感子串，仅靠字段名遍历无法命中）。
        String masked = SensitiveDataUtils.maskJson(
            "{\"configKey\":\"admin.OAUTH_CLIENT_SECRET\",\"configValue\":\"raw-secret-value\",\"description\":\"keep\"}");

        assertFalse(masked.contains("raw-secret-value"));
        assertTrue(masked.contains("\"configValue\":\"***\""));
        assertTrue(masked.contains("\"configKey\":\"admin.OAUTH_CLIENT_SECRET\""));
        assertTrue(masked.contains("\"description\":\"keep\""));
    }

    @Test
    void shouldKeepConfigValueWhenConfigKeyIsNotSecretClass() {
        // P1#1：非密钥类配置键（如 admin.LOGIN_CAPTCHA_ENABLED）的 configValue 保留原样，不误掩码。
        String masked = SensitiveDataUtils.maskJson(
            "{\"configKey\":\"admin.LOGIN_CAPTCHA_ENABLED\",\"configValue\":\"true\",\"description\":\"keep\"}");

        assertTrue(masked.contains("\"configValue\":\"true\""));
        assertFalse(masked.contains("\"configValue\":\"***\""));
    }

    @Test
    void shouldMaskSecretClassConfigValueEvenWhenScalarJson() {
        // P1#1：configValue 为标量/纯文本 JSON（非对象/数组）时原 maskEmbeddedJson 返回 null 不脱敏，
        // 密钥类配置键路径需整体掩码。
        String masked = SensitiveDataUtils.maskJson(
            "{\"configKey\":\"permission.SOME_API_TOKEN\",\"configValue\":\"{\\\"plain\\\":\\\"tok123\\\"}\"}");

        assertFalse(masked.contains("tok123"));
        assertTrue(masked.contains("\"configValue\":\"***\""));
    }

    @Test
    void shouldRecognizeKeySuffixSecretConfigKeysViaPublicApi() {
        // * KEY 结尾的签名/加密/API 等凭证类键名应判定为密钥类（"key" 按后缀匹配）；
        // 名称中仅偶然含 key 子串的普通配置键（KEYBOARD_LAYOUT/HOTKEY_ENABLED/MONKEY_MODE 等）
        // 不得误判——否则其 configValue 被误掩码、丢失审计可追溯性。
        assertTrue(SensitiveDataUtils.isSecretConfigKey("admin.SIGNING_KEY"), "签名密钥应判定为密钥类");
        assertTrue(SensitiveDataUtils.isSecretConfigKey("admin.ENCRYPTION_KEY"), "加密密钥应判定为密钥类");
        assertTrue(SensitiveDataUtils.isSecretConfigKey("admin.OAUTH_CLIENT_SECRET"));
        assertTrue(SensitiveDataUtils.isSecretConfigKey("admin.API_TOKEN"));
        assertTrue(SensitiveDataUtils.isSecretConfigKey("admin.API_KEY"), "API_KEY 后缀应判定为密钥类");
        // 名称中间含 key、但非 *_KEY 结尾的普通配置，不得误判
        assertFalse(SensitiveDataUtils.isSecretConfigKey("admin.KEYBOARD_LAYOUT"));
        assertFalse(SensitiveDataUtils.isSecretConfigKey("admin.HOTKEY_ENABLED"));
        assertFalse(SensitiveDataUtils.isSecretConfigKey("admin.MONKEY_MODE"));
        assertFalse(SensitiveDataUtils.isSecretConfigKey("admin.CACHE_KEY_PREFIX"));
        assertFalse(SensitiveDataUtils.isSecretConfigKey("admin.KEY_ROTATION_DAYS"));
        assertFalse(SensitiveDataUtils.isSecretConfigKey("admin.LOGIN_CAPTCHA_ENABLED"));
        assertFalse(SensitiveDataUtils.isSecretConfigKey("admin.FILE_ALLOWED_TYPES"));
        assertFalse(SensitiveDataUtils.isSecretConfigKey(null));
    }

    @Test
    void shouldMaskConfigValueForKeySuffixSecretConfigKey() {
        // 跨字段规则对 * KEY 结尾的凭证配置键同样生效（JSON 场景下键名以 key 结尾）。
        String masked = SensitiveDataUtils.maskJson(
            "{\"configKey\":\"admin.SIGNING_KEY\",\"configValue\":\"new-signing-secret\",\"description\":\"keep\"}");

        assertFalse(masked.contains("new-signing-secret"));
        assertTrue(masked.contains("\"configValue\":\"***\""));
        assertTrue(masked.contains("\"description\":\"keep\""));
    }
}
