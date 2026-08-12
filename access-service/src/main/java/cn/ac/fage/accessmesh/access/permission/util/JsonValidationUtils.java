package cn.ac.fage.accessmesh.access.permission.util;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * JSON校验工具类
 * <p>
 * 提供JSON字符串校验方法，确保写入JSONB列的数据格式正确。
 * </p>
 */
public final class JsonValidationUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonValidationUtils() {
    }

    /**
     * 校验字符串是否为合法JSON
     *
     * @param value 待校验字符串
     * @throws IllegalArgumentException 如果不是合法JSON
     */
    public static void validateJson(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("JSON值不能为空");
        }
        try {
            MAPPER.readTree(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON格式不合法: " + e.getMessage());
        }
    }
}