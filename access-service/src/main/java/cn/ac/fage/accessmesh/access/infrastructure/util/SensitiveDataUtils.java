package cn.ac.fage.accessmesh.access.infrastructure.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

/**
 * 敏感数据脱敏工具（T-ACCESS-007）。
 * <p>
 * 用于 operation_log 等审计日志写入前对请求体/摘要中的敏感字段值脱敏，
 * 保证密码、验证码、Token、密钥等不会明文入库（access-service-architecture §8.2）。
 * 脱敏采用 Jackson 递归树遍历：将 JSON 解析为对象树，按字段名（忽略大小写与下划线）
 * 是否包含敏感子串判定，命中敏感字段名时将其值替换为掩码 {@value #MASK}。
 * </p>
 * <p>
 * 相比纯正则方案，树遍历能覆盖所有值的形态——标量字符串/数字/布尔、嵌套对象、
 * 数组内的元素、以及值本身是内嵌 JSON 字符串的字段（如配置值存 JSON 的
 * {@code SystemConfigReq.configValue}）：只要字段名命中敏感子串，无论其值结构如何
 * 都整体替换为掩码，杜绝把敏感内容明文写入审计列。
 * </p>
 */
public final class SensitiveDataUtils {

    /** 脱敏掩码 */
    public static final String MASK = "***";

    /** 截断省略号（占 {@value #REQUEST_BODY_MAX_LEN} 中 3 个字符，截断后总长不超上限） */
    public static final String ELLIPSIS = "...";

    /** 请求体入库上限（operation_log.request_body VARCHAR(4000)，超长截断到该值，含省略号） */
    public static final int REQUEST_BODY_MAX_LEN = 4000;

    /**
     * 敏感字段名匹配子串（小写、去下划线后 contains 匹配）。
     * 覆盖密码/验证码/短信码/令牌/密钥/API Key 等，以及 PII（手机号/邮箱/身份证/私人证书），
     * 字段名含任一子串即脱敏。普通业务字段名经去下划线归一后不会误命中
     * （如 {@code corporateName} 不含这些子串）。
     */
    private static final String[] SENSITIVE_TERMS = {
        "password", "pwd", "secret", "token", "smscode", "captchacode", "apikey", "authorization",
        "phone", "mobile", "email", "idcard", "idcardno", "certificate", "privatekey", "privatekeypem"
    };

    /** 精确字段名敏感集合（小写、去下划线后整体相等匹配，而非 contains）。
     * 用于 contains 子串会误命中普通业务字段的敏感名：
     * {@code code} 为 OAuth2 授权码（短期凭证），但「code」子串会命中 {@code serviceCode} /
     * {@code roleCode} / {@code resourceCode} / {@code typeCode} 等合法字段，故只能精确匹配、
     * 不得加入 {@link #SENSITIVE_TERMS}。{@code codeverifier} 为 PKCE 验证器（去下划线归一后）。 */
    private static final String[] PRECISE_SENSITIVE_FIELDS = {
        "code", "codeverifier"
    };

    /** 密钥类配置键名匹配子串（大写、去下划线后 contains 匹配）。
     * 用于 {@code SystemConfigReq} 的跨字段脱敏：当 JSON 中同时出现 {@code configKey} 与
     * {@code configValue} 且配置键名命中密钥类子串（如 {@code admin.OAUTH_CLIENT_SECRET}），
     * 将同级 {@code configValue} 整体掩码——因该值可能是纯文本/标量 JSON，字段名
     * {@code configValue} 本身不含敏感子串，仅靠按 JSON 字段名匹配的树遍历无法脱敏。 */
    private static final String[] SECRET_CONFIG_KEY_TERMS = {
        "password", "secret", "token", "apikey", "privatekey"
    };

    /** 树遍历用 ObjectMapper（仅用于解析/序列化，线程安全）。 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private SensitiveDataUtils() {
    }

    /**
     * 对 JSON 字符串中的敏感字段值脱敏（递归树遍历）。
     *
     * @param json 原始 JSON（可为 null）
     * @return 脱敏后的 JSON；null 返回 null；非合法 JSON（纯文本、空串）原样返回
     */
    public static String maskJson(String json) {
        if (json == null || json.isBlank()) {
            return json;
        }
        JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(json);
        } catch (Exception e) {
            // 非合法 JSON（可能是纯文本请求体）：无法安全解析，原样返回不阻断审计
            return json;
        }
        if (root == null) {
            return json;
        }
        JsonNode masked = maskNode(root);
        try {
            return OBJECT_MAPPER.writeValueAsString(masked);
        } catch (Exception e) {
            // 序列化失败（极端情况）：退回原始 JSON，避免阻断审计主流程
            return json;
        }
    }

    /**
     * 递归脱敏节点。
     * <p>
     * 对象节点：遍历字段，字段名敏感则整体替换其值为掩码，否则递归子节点；
     * 数组节点：逐元素递归（元素自身若为对象，其敏感字段同样被处理）。
     * </p>
     *
     * @param node 待脱敏节点
     * @return 脱敏后的节点（可能为同一个实例的修改，或掩码文本节点）
     */
    private static JsonNode maskNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return node;
        }
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            // 跨字段规则：先识别「configKey + configValue」对，配置键名命中密钥类时掩码同级 configValue
            // （见 SECRET_CONFIG_KEY_TERMS），无论 configValue 是纯文本/标量/对象/数组。
            String configKeyValue = findFieldText(obj, "configkey");
            if (configKeyValue != null && isSecretConfigKey(configKeyValue)) {
                ObjectNode valueHolder = (ObjectNode) obj;
                valueHolder.set("configValue", TextNode.valueOf(MASK));
            }
            obj.fields().forEachRemaining(entry -> {
                String fieldName = entry.getKey();
                JsonNode value = entry.getValue();
                if (isSensitiveField(fieldName) && value != null && !value.isNull()) {
                    // 命中敏感字段名：无论值结构（标量/对象/数组/内嵌JSON字符串）整体替换为掩码
                    obj.set(fieldName, TextNode.valueOf(MASK));
                } else if (value != null && (value.isObject() || value.isArray())) {
                    obj.set(fieldName, maskNode(value));
                } else if (value != null && value.isTextual()) {
                    // 值为 JSON 文本字符串（如 SystemConfigReq.configValue 存嵌套 JSON）：
                    // 尝试解析为对象/数组树并递归脱敏，再序列化回字符串，防止内层敏感键明文入库
                    String inner = maskEmbeddedJson(value.asText());
                    if (inner != null) {
                        obj.set(fieldName, TextNode.valueOf(inner));
                    }
                }
            });
            return obj;
        }
        if (node.isArray()) {
            ArrayNode arr = (ArrayNode) node;
            // 数组元素可能是标量字符串 JSON 或对象；逐项处理
            for (int i = 0; i < arr.size(); i++) {
                JsonNode element = arr.get(i);
                if (element == null || element.isNull()) {
                    continue;
                }
                if (element.isObject() || element.isArray()) {
                    arr.set(i, maskNode(element));
                } else if (element.isTextual()) {
                    String inner = maskEmbeddedJson(element.asText());
                    if (inner != null) {
                        arr.set(i, TextNode.valueOf(inner));
                    }
                }
            }
            return arr;
        }
        return node;
    }

    /**
     * 对值为 JSON 文本字符串的内部内容脱敏。
     * <p>
     * 字符串能被解析为 JSON 时递归处理（内层对象/数组的敏感字段同样掩码）后序列化返回；
     * 不可解析（纯文本、非 JSON）返回 null，由调用方保持原值。
     * </p>
     *
     * @param text 字段值字符串（可能是嵌套 JSON）
     * @return 脱敏后的 JSON 文本；非 JSON 文本返回 null
     */
    private static String maskEmbeddedJson(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        JsonNode inner;
        try {
            inner = OBJECT_MAPPER.readTree(text);
        } catch (Exception e) {
            return null;
        }
        if (inner == null || (!inner.isObject() && !inner.isArray())) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(maskNode(inner));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 对请求体脱敏并限长（脱敏 → 截断）。
     * <p>
     * 超长截断为 {@code maxLen} 内最长的“截断内容 + 省略号”：先保留
     * {@code maxLen - 3} 个字符，再追加省略号，总长不超过 {@code maxLen}
     * （数据库列 VARCHAR(4000)，若截断后仍超列上限，插入会失败并丢失整条审计日志）。
     * </p>
     *
     * @param json   原始请求体 JSON
     * @param maxLen 最大长度（超出截断并追加省略号，总长不超过 maxLen）
     * @return 脱敏并限长后的字符串；null 返回 null
     */
    public static String maskRequestBody(String json, int maxLen) {
        if (json == null) {
            return null;
        }
        String masked = maskJson(json);
        if (masked.length() > maxLen) {
            int keep = Math.max(0, maxLen - ELLIPSIS.length());
            return masked.substring(0, keep) + ELLIPSIS;
        }
        return masked;
    }

    /**
     * 判断字段名是否敏感：先精确匹配 {@link #PRECISE_SENSITIVE_FIELDS}（整体相等，
     * 命中 {@code code}/{@code codeVerifier} 等），再回退 to contains 匹配
     * {@link #SENSITIVE_TERMS}。两者皆以小写去下划线归一后比较。
     *
     * @param fieldName JSON 字段名
     * @return true=敏感
     */
    private static boolean isSensitiveField(String fieldName) {
        if (fieldName == null || fieldName.isEmpty()) {
            return false;
        }
        String normalized = normalize(fieldName);
        if (PRECISE_SENSITIVE_FIELDS.length > 0) {
            for (String precise : PRECISE_SENSITIVE_FIELDS) {
                if (precise.equals(normalized)) {
                    return true;
                }
            }
        }
        for (String term : SENSITIVE_TERMS) {
            if (normalized.contains(term)) {
                return true;
            }
        }
        return false;
    }

    /** 归一化字段名：小写并去除下划线。 */
    private static String normalize(String name) {
        return name.toLowerCase().replace("_", "");
    }

    /** 从对象节点取某字段的字符串值（字段名归一化后整体相等匹配）；无则返回 null。 */
    private static String findFieldText(ObjectNode obj, String targetNormalized) {
        if (obj == null || obj.size() == 0) {
            return null;
        }
        var it = obj.fields();
        while (it.hasNext()) {
            var entry = it.next();
            if (normalize(entry.getKey()).equals(targetNormalized) && entry.getValue() != null
                && entry.getValue().isTextual()) {
                return entry.getValue().asText();
            }
        }
        return null;
    }

    /** 配置键名是否命中密钥类子串（如 {@code admin.OAUTH_CLIENT_SECRET}）。 */
    private static boolean isSecretConfigKey(String configKey) {
        if (configKey == null || configKey.isBlank()) {
            return false;
        }
        String normalized = normalize(configKey);
        for (String term : SECRET_CONFIG_KEY_TERMS) {
            if (normalized.contains(term)) {
                return true;
            }
        }
        return false;
    }
}
