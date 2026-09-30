package cn.ac.fage.accessmesh.access.engine.query;

import cn.ac.fage.accessmesh.access.infrastructure.util.HttpRequestUtils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Iterator;
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
 * （null/String/Boolean/有限数值/List/Map，数值限不可变标准实现），拒绝循环引用与任意可变 Java 对象；
 * 顶层 null 键与各层 null 值静默过滤（顶层过滤沿 {@code PermEvalContext} 先例），
 * 嵌套 Map 键必须为 String——null 等非 String 键拒绝。
 * 嵌套集合在构造时深度复制为不可变结构——构造后修改源集合不影响执行输入（C07）。
 * 保留键仅约束顶层：嵌套 Map 内的同名键位于调用方自定义命名空间；未来条件展平实现
 * 必须保持该语义（不得把嵌套同名键递归提升为顶层覆盖保留键）。
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

    /**
     * 从当前 HTTP 请求装配可信 clientIp（无请求上下文时 IP 为空——IP 类条件 fail-closed）。
     * <p>
     * 服务内 EVALUATE 面共用的装配口径（T-PERM-091 外评处置收编：QueryGate 判定面与
     * 视图权限串/菜单面统一，防逐点手写漏装——旧引擎入口对无上下文查询的自动装配等价物）。
     * </p>
     */
    public static CallerContext ofCurrentRequest() {
        return new CallerContext(HttpRequestUtils.getClientIp(HttpRequestUtils.currentRequest()), Map.of());
    }

    /** SDK 契约 {@code context.clientIp} 键提取为受信 IP，其余键归调用方属性（适配层唯一入口，
     *  T-PERM-089/090 消费面共用；保留键由结构拒绝的行为同构造器）。 */
    public static CallerContext fromCallerMap(Map<String, Object> context) {
        if (context == null || context.isEmpty()) {
            return of(null);
        }
        String clientIp = null;
        Map<String, Object> rest = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            if (KEY_CLIENT_IP.equals(entry.getKey())) {
                clientIp = entry.getValue() == null ? null : String.valueOf(entry.getValue());
            } else {
                rest.put(entry.getKey(), entry.getValue());
            }
        }
        return new CallerContext(clientIp, rest);
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
            Object value = deepJsonCopy(entry.getValue());
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
     * @return 不可变深拷贝；null 表示该值被过滤
     */
    private static Object deepJsonCopy(Object value) {
        if (!(value instanceof List<?>) && !(value instanceof Map<?, ?>)) return scalarCopy(value);
        // 用堆上迭代帧代替 Java 递归栈，使深上下文能到达服务端预算检查。
        ArrayDeque<JsonFrame> frames = new ArrayDeque<>();
        IdentityHashMap<Object, Boolean> activePath = new IdentityHashMap<>();
        frames.push(new JsonFrame(value, null));
        activePath.put(value, true);
        while (true) {
            JsonFrame frame = frames.peek();
            if (!frame.iterator.hasNext()) {
                Object copy = frame.finish();
                frames.pop();
                activePath.remove(frame.source);
                if (frames.isEmpty()) return copy;
                frames.peek().add(frame.parentKey, copy);
                continue;
            }
            Object child = frame.iterator.next();
            String key = null;
            if (frame.map != null) {
                Map.Entry<?, ?> entry = (Map.Entry<?, ?>) child;
                if (!(entry.getKey() instanceof String text)) {
                    throw new QueryValidationException("attributes 嵌套 Map 键必须为 String: " + entry.getKey());
                }
                key = text;
                child = entry.getValue();
            }
            if (child instanceof List<?> || child instanceof Map<?, ?>) {
                if (activePath.put(child, true) != null) throw new QueryValidationException("attributes 拒绝循环引用");
                frames.push(new JsonFrame(child, key));
            } else {
                frame.add(key, scalarCopy(child));
            }
        }
    }

    private static final class JsonFrame {
        final Object source;
        final String parentKey;
        final Iterator<?> iterator;
        final Map<String, Object> map;
        final List<Object> list;

        JsonFrame(Object source, String parentKey) {
            this.source = source;
            this.parentKey = parentKey;
            if (source instanceof Map<?, ?> values) {
                iterator = values.entrySet().iterator();
                map = new LinkedHashMap<>(values.size());
                list = null;
            } else {
                List<?> values = (List<?>) source;
                iterator = values.iterator();
                map = null;
                list = new ArrayList<>(values.size());
            }
        }

        void add(String key, Object value) {
            if (value == null) return;
            if (map != null) map.put(key, value);
            else list.add(value);
        }

        Object finish() { return map != null ? Map.copyOf(map) : List.copyOf(list); }
    }

    private static Object scalarCopy(Object value) {
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
        throw new QueryValidationException("attributes 仅接受 JSON 值，拒绝: " + value.getClass().getName());
    }

    /** 数值白名单：不可变标准 Number（JSON number 可映射集合）——可变实现（Atomic* 等）与非标准子类拒绝；
     *  BigInteger/BigDecimal 非 final，instanceof 会放行其可变子类，须精确类型判断。 */
    private static boolean isImmutableJsonNumber(Object value) {
        return value instanceof Integer || value instanceof Long || value instanceof Short
            || value instanceof Byte
            || value.getClass() == BigInteger.class || value.getClass() == BigDecimal.class;
    }

    private static void requireFinite(double d) {
        if (!Double.isFinite(d)) {
            throw new QueryValidationException("attributes 数值必须为有限值（拒绝 NaN/Infinity）");
        }
    }
}
