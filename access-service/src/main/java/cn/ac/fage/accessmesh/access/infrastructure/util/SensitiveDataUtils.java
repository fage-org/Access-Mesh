package cn.ac.fage.accessmesh.access.infrastructure.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

/**
 * 敏感数据脱敏工具（T-ACCESS-007）。
 * <p>
 * <b>读取面脱敏（T-ACCESS-085）</b>：审计响应复用敏感字段树遍历，文本补手机号与邮箱固定规则。
 * 普通用户名、完整 IP 与原始审计记录保留；不恢复 operation_log.request_body 写入。
 * 原有载荷写入/作用域字典扩展冻结边界保持，不建设可配置脱敏规则。
 * </p>
 * <p>
 * 原用于 operation_log 等审计日志写入前对请求体/摘要中的敏感字段值脱敏
 * （历史写入用途；当前审计响应侧掩码密码、验证码、Token、密钥等，原始审计列保留）
 * （access-service-architecture §8.2）。
 * 脱敏采用 Jackson 递归树遍历：将 JSON 解析为对象树，按字段名（忽略大小写与下划线）
 * 是否包含敏感子串判定，命中敏感字段名时将其值替换为掩码 {@value #MASK}。
 * </p>
 * <p>
 * 相比纯正则方案，树遍历能覆盖所有值的形态——标量字符串/数字/布尔、嵌套对象、
 * 数组内的元素、以及值本身是内嵌 JSON 字符串的字段（如配置值存 JSON 的
 * {@code SystemConfigReq.configValue}）：只要字段名命中敏感子串，无论其值结构如何
 * 都整体替换为掩码；文本值补充手机号/邮箱匹配，不修改数字节点与字段名。
 * </p>
 */
public final class SensitiveDataUtils {

    private static final java.util.regex.Pattern PHONE = java.util.regex.Pattern.compile("(?<![0-9])1[3-9][0-9]{9}(?![0-9])");
    private static final java.util.regex.Pattern EMAIL = java.util.regex.Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    /** 审计读取面固定规则：仅掩码明确的手机号/邮箱，普通用户名及 IP 保留。 */
    public static String maskText(String text) {
        if (text == null) return null;
        return EMAIL.matcher(PHONE.matcher(text).replaceAll(MASK)).replaceAll(MASK);
    }

    /** 保留原始审计列；只转换返回给调用方的快照。 */
    public static String maskAuditSnapshot(String json) {
        return maskJson(json, null, true);
    }

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
     * 仅保留 {@code codeverifier}（PKCE 验证器，全局唯一无业务碰撞）。
     * {@code code}（OAuth2 授权码）不在此全局集合——「code」同名业务字段（组织/资源编码
     * {@code OrgUpdateReq.code} / {@code ResourceCreateReq.code} 等）会被误掩码为 {@code ***}，
     * 降低审计追溯价值。原「按调用作用域并入精确匹配」机制（经运行时上下文登记）
     * 已随 T-ACCESS-025 操作日志参数序列化收敛移除；本工具冻结保留 {@code extraPreciseFields}
     * 参数形态不变。 */
    private static final String[] PRECISE_SENSITIVE_FIELDS = {
        "codeverifier"
    };

    /** 密钥类配置键名匹配子串（大写、去下划线后 contains 匹配）。
     * 用于配置键的密钥类判定：键名命中任一子串（如 {@code admin.OAUTH_CLIENT_SECRET}、
     * {@code admin.API_TOKEN}）即视为敏感配置。原由服务端基于入库实体的真实 configKey 调用
     * （admin /config 更新入口——调用随 T-ACCESS-025 删除，入口整链随 T-ACCESS-037 退役），
     * 而非信任客户端请求字段；当前无生产调用方，冻结保留判定规则。凭证独有词按 contains 匹配即可，
     * 不会误伤普通配置；通用词 {@code key} 见 {@link #SECRET_CONFIG_KEY_SUFFIX_TERMS}。 */
    private static final String[] SECRET_CONFIG_KEY_TERMS = {
        "password", "secret", "token", "apikey", "privatekey"
    };

    /** 凭证键名后缀匹配词（{@code *_KEY} 结尾的签名/加密/API 等凭证类键名，如
     * {@code admin.SIGNING_KEY} / {@code admin.ENCRYPTION_KEY}）。
     * {@code key} 为通用词，只能按后缀精确匹配——contains 会误伤 KEYBOARD_LAYOUT / HOTKEY_ENABLED /
     * MONKEY_MODE / CACHE_KEY_PREFIX / KEY_ROTATION_DAYS 等普通配置键（其 {@code configKey} 内含
     * {@code key} 子串并非密钥），导致这些合法配置的 {@code configValue} 被误掩码、丢失审计可追溯性。 */
    private static final String[] SECRET_CONFIG_KEY_SUFFIX_TERMS = {
        "key"
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
        return maskJson(json, null);
    }

    /**
     * 对 JSON 字符串中的敏感字段值脱敏（递归树遍历），支持按调用作用域并入额外精确字段名。
     *
     * @param json              原始 JSON（可为 null）
     * @param extraPreciseFields 调用作用域并入的精确字段名集合（小写去下划线归一后整体相等匹配）；
     *                           冻结保留的参数形态，当前无生产调用方传入非 null 值；
     *                           为 null 时等价于 {@link #maskJson(String)}
     * @return 脱敏后的 JSON；null 返回 null；非合法 JSON（纯文本、空串）原样返回
     */
    public static String maskJson(String json, java.util.Set<String> extraPreciseFields) {
        return maskJson(json, extraPreciseFields, false);
    }

    private static String maskJson(String json, java.util.Set<String> extraPreciseFields,
                                   boolean maskTextValues) {
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
        JsonNode masked = maskNode(root, extraPreciseFields, maskTextValues);
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
     * @param node               待脱敏节点
     * @param extraPreciseFields 调用作用域并入的额外精确字段名集合（可为 null）
     * @return 脱敏后的节点（可能为同一个实例的修改，或掩码文本节点）
     */
    private static JsonNode maskNode(JsonNode node, java.util.Set<String> extraPreciseFields, boolean maskTextValues) {
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
                if (isSensitiveField(fieldName, extraPreciseFields) && value != null && !value.isNull()) {
                    // 命中敏感字段名：无论值结构（标量/对象/数组/内嵌JSON字符串）整体替换为掩码
                    obj.set(fieldName, TextNode.valueOf(MASK));
                } else if (value != null && (value.isObject() || value.isArray())) {
                    obj.set(fieldName, maskNode(value, extraPreciseFields, maskTextValues));
                } else if (value != null && value.isTextual()) {
                    // 值为 JSON 文本字符串（如 SystemConfigReq.configValue 存嵌套 JSON）：
                    // 尝试解析为对象/数组树并递归脱敏，再序列化回字符串，防止内层敏感键明文返回
                    String inner = maskEmbeddedJson(value.asText(), extraPreciseFields, maskTextValues);
                    if (inner != null) {
                        obj.set(fieldName, TextNode.valueOf(inner));
                    } else if (maskTextValues) {
                        obj.set(fieldName, TextNode.valueOf(maskText(value.asText())));
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
                    arr.set(i, maskNode(element, extraPreciseFields, maskTextValues));
                } else if (element.isTextual()) {
                    String inner = maskEmbeddedJson(element.asText(), extraPreciseFields, maskTextValues);
                    if (inner != null) {
                        arr.set(i, TextNode.valueOf(inner));
                    } else if (maskTextValues) {
                        arr.set(i, TextNode.valueOf(maskText(element.asText())));
                    }
                }
            }
            return arr;
        }
        if (maskTextValues && node.isTextual()) {
            String inner = maskEmbeddedJson(node.asText(), extraPreciseFields, true);
            return TextNode.valueOf(inner != null ? inner : maskText(node.asText()));
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
     * @param text               字段值字符串（可能是嵌套 JSON）
     * @param extraPreciseFields 调用作用域并入的额外精确字段名集合（可为 null）
     * @return 脱敏后的 JSON 文本；非 JSON 文本返回 null
     */
    private static String maskEmbeddedJson(String text, java.util.Set<String> extraPreciseFields, boolean maskTextValues) {
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
            return OBJECT_MAPPER.writeValueAsString(maskNode(inner, extraPreciseFields, maskTextValues));
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
        return maskRequestBody(json, maxLen, null);
    }

    /**
     * 对请求体脱敏并限长（脱敏 → 截断），支持并入调用作用域额外精确字段名。
     * <p>
     * 超长截断为 {@code maxLen} 内最长的“截断内容 + 省略号”：先保留
     * {@code maxLen - 3} 个字符，再追加省略号，总长不超过 {@code maxLen}
     * （数据库列 VARCHAR(4000)，若截断后仍超列上限，插入会失败并丢失整条审计日志）。
     * </p>
     *
     * @param json               原始请求体 JSON
     * @param maxLen             最大长度（超出截断并追加省略号，总长不超过 maxLen）
     * @param extraPreciseFields 调用作用域并入的额外精确字段名集合（可为 null）
     * @return 脱敏并限长后的字符串；null 返回 null
     */
    public static String maskRequestBody(String json, int maxLen,
                                         java.util.Set<String> extraPreciseFields) {
        if (json == null) {
            return null;
        }
        String masked = maskJson(json, extraPreciseFields);
        if (masked.length() > maxLen) {
            int keep = Math.max(0, maxLen - ELLIPSIS.length());
            return masked.substring(0, keep) + ELLIPSIS;
        }
        return masked;
    }

    /**
     * 判断字段名是否敏感：先精确匹配（全局 {@link #PRECISE_SENSITIVE_FIELDS} + 调用作用域
     * {@code extraPreciseFields}，两者皆小写去下划线归一后整体相等），再回退 to contains 匹配
     * {@link #SENSITIVE_TERMS}。
     *
     * @param fieldName           JSON 字段名
     * @param extraPreciseFields 调用作用域并入的额外精确字段名集合（可为 null）
     * @return true=敏感
     */
    private static boolean isSensitiveField(String fieldName, java.util.Set<String> extraPreciseFields) {
        if (fieldName == null || fieldName.isEmpty()) {
            return false;
        }
        String normalized = normalize(fieldName);
        for (String precise : PRECISE_SENSITIVE_FIELDS) {
            if (precise.equals(normalized)) {
                return true;
            }
        }
        if (extraPreciseFields != null) {
            for (String precise : extraPreciseFields) {
                if (precise != null && precise.equals(normalized)) {
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

    /**
     * 配置键名是否为密钥类（如 {@code admin.OAUTH_CLIENT_SECRET} / {@code admin.SIGNING_KEY}）。
     * <p>
     * 原供服务端基于入库实体的真实 {@code configKey} 判定是否需在审计请求体中掩码 {@code configValue}
     * （admin /config 更新入口——调用随 T-ACCESS-025 删除，入口整链随 T-ACCESS-037 退役），而非信任客户端请求字段；
     * 亦用于 {@code maskNode} 的 configKey+configValue 跨字段脱敏规则（冻结保留）。
     * 当前无生产调用方，随本工具冻结保留。归一化去下划线后，对 {@link #SECRET_CONFIG_KEY_TERMS}
     * 做 contains 匹配，对 {@link #SECRET_CONFIG_KEY_SUFFIX_TERMS}（通用词 {@code key}）做 endsWith
     * 后缀匹配——避免 {@code key} 的 contains 误伤名称中偶然含 {@code key} 子串的普通配置键。
     * </p>
     *
     * @param configKey 配置键名
     * @return true=密钥类配置
     */
    public static boolean isSecretConfigKey(String configKey) {
        if (configKey == null || configKey.isBlank()) {
            return false;
        }
        String normalized = normalize(configKey);
        for (String term : SECRET_CONFIG_KEY_TERMS) {
            if (normalized.contains(term)) {
                return true;
            }
        }
        for (String suffix : SECRET_CONFIG_KEY_SUFFIX_TERMS) {
            if (normalized.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }
}
