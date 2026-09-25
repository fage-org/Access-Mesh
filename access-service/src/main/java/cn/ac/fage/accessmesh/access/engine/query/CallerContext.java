package cn.ac.fage.accessmesh.access.engine.query;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 可信调用上下文（T-PERM-082，设计 §2.2）。
 * <p>
 * 可信 clientIp＋受限 JSON 属性；服务端固定评估时刻（注入 Clock，RunState 持有），
 * 不接受普通客户端覆盖时钟。保留键 {@code clientIp/evaluatedAt/timestamp}
 * 不得通过 attributes 覆盖——顶层出现即拒绝构造。属性值只接受 JSON 值
 * （null/String/Boolean/有限数值/List/Map），拒绝循环引用与任意可变 Java 对象；
 * null 键与 null 值（含嵌套）沿 {@code PermEvalContext} 先例静默过滤。
 * 嵌套集合在构造时深度复制为不可变结构——构造后修改源集合不影响执行输入（C07）。
 * 保留键仅约束顶层：嵌套 Map 内的同名键位于调用方自定义命名空间，不参与展平覆盖。
 * </p>
 *
 * @param clientIp   可信客户端 IP（Gateway 重建），可空=无请求上下文
 * @param attributes 调用方扩展属性；null 归一为空 Map
 */
public record CallerContext(String clientIp, Map<String, Object> attributes) {

    /** 保留键：客户端 IP（不可经 attributes 覆盖）。 */
    public static final String KEY_CLIENT_IP = "clientIp";

    /** 保留键：评估时刻（服务器环境专属，不可经 attributes 伪造）。 */
    public static final String KEY_EVALUATED_AT = "evaluatedAt";

    /** 保留键：时间戳（服务器环境专属，不可经 attributes 伪造）。 */
    public static final String KEY_TIMESTAMP = "timestamp";

    private static final Set<String> RESERVED_KEYS = Set.of(KEY_CLIENT_IP, KEY_EVALUATED_AT, KEY_TIMESTAMP);

    public CallerContext {
        attributes = copyAttributes(attributes);
    }

    /** 无扩展属性的上下文。 */
    public static CallerContext of(String clientIp) {
        return new CallerContext(clientIp, Map.of());
    }

    private static Map<String, Object> copyAttributes(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = entry.getKey();
            if (key == null) {
                continue;
            }
            if (RESERVED_KEYS.contains(key)) {
                throw new QueryValidationException("attributes 保留键不可覆盖: " + key);
            }
            Object value = deepJsonCopy(entry.getValue(), new ArrayList<>());
            if (value != null) {
                copy.put(key, value);
            }
        }
        return Map.copyOf(copy);
    }

    /**
     * 深度校验并复制 JSON 值。
     *
     * @param value 待校验值
     * @param path 当前引用路径（身份判等检测循环）
     * @return 不可变深拷贝；null 表示该值被过滤
     */
    private static Object deepJsonCopy(Object value, List<Object> path) {
        if (value == null) {
            return null;
        }
        if (value instanceof String || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Double d) {
            requireFinite(d);
            return value;
        }
        if (value instanceof Float f) {
            requireFinite(f.doubleValue());
            return value;
        }
        if (isImmutableJsonNumber(value)) {
            return value;
        }
        if (value instanceof List<?> list) {
            requireAcyclic(value, path);
            path.add(value);
            try {
                List<Object> copy = new ArrayList<>(list.size());
                for (Object element : list) {
                    Object copied = deepJsonCopy(element, path);
                    if (copied != null) {
                        copy.add(copied);
                    }
                }
                return List.copyOf(copy);
            } finally {
                path.remove(path.size() - 1);
            }
        }
        if (value instanceof Map<?, ?> map) {
            requireAcyclic(value, path);
            path.add(value);
            try {
                Map<String, Object> copy = new LinkedHashMap<>(map.size());
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (!(entry.getKey() instanceof String key)) {
                        throw new QueryValidationException("attributes 嵌套 Map 键必须为 String: " + entry.getKey());
                    }
                    Object copied = deepJsonCopy(entry.getValue(), path);
                    if (copied != null) {
                        copy.put(key, copied);
                    }
                }
                return Map.copyOf(copy);
            } finally {
                path.remove(path.size() - 1);
            }
        }
        throw new QueryValidationException("attributes 仅接受 JSON 值，拒绝: " + value.getClass().getName());
    }

    /** 数值白名单：不可变标准 Number（JSON number 可映射集合）——可变实现（Atomic* 等）与非标准子类拒绝。 */
    private static boolean isImmutableJsonNumber(Object value) {
        return value instanceof Integer || value instanceof Long || value instanceof Short
            || value instanceof Byte || value instanceof BigInteger || value instanceof BigDecimal;
    }

    private static void requireAcyclic(Object value, List<Object> path) {
        for (Object ancestor : path) {
            if (ancestor == value) {
                throw new QueryValidationException("attributes 拒绝循环引用");
            }
        }
    }

    private static void requireFinite(double d) {
        if (!Double.isFinite(d)) {
            throw new QueryValidationException("attributes 数值必须为有限值（拒绝 NaN/Infinity）");
        }
    }
}
