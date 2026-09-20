package cn.ac.fage.accessmesh.access.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 发布请求指纹的对象键排序；数组顺序由具体发布协议规范化，不作数值有损转换。 */
public final class CanonicalJson {
    private CanonicalJson() {}
    public static String text(JsonNode node) { return sort(node).toString(); }
    public static String hash(JsonNode node) { return SyncKeyCodecUtil.sha256Hex(text(node)); }
    public static int compareText(String left, String right) {
        return Arrays.compareUnsigned(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }
    private static JsonNode sort(JsonNode node) {
        if (node.isObject()) {
            var result = JsonNodeFactory.instance.objectNode();
            List<String> keys = new ArrayList<>();
            node.fieldNames().forEachRemaining(keys::add);
            keys.sort(CanonicalJson::compareText);
            for (String key : keys) result.set(key, sort(node.get(key)));
            return result;
        }
        if (node.isArray()) {
            var result = JsonNodeFactory.instance.arrayNode();
            node.forEach(value -> result.add(sort(value)));
            return result;
        }
        return node;
    }
}
