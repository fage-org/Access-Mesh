package cn.ac.fage.accessmesh.access.audit.util;

import cn.ac.fage.accessmesh.access.infrastructure.enums.AccessErrorCode;
import cn.ac.fage.accessmesh.common.exception.BizException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

/** 写入 JSONB 前校验完整单根 JSON；外部字段与内部聚合快照的容量边界独立。 */
public final class JsonValidationUtils {
    private static final int MAX_INPUT_BYTES = 64 * 1024;
    private static final ObjectMapper MAPPER = new ObjectMapper()
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private JsonValidationUtils() {}

    /** 外部 JSON 字段：UTF-8 最多 64 KiB，非法输入返回公开参数错误码。 */
    public static void validateJson(String value) {
        if (value != null && value.getBytes(StandardCharsets.UTF_8).length > MAX_INPUT_BYTES) {
            throw invalid("JSON 字段不能超过 64 KiB");
        }
        try {
            validateSnapshot(value);
        } catch (IllegalArgumentException ex) {
            throw invalid(ex.getMessage());
        }
    }

    /** 内部审计聚合快照：只校验语法，不施加外部字段长度限制。 */
    public static void validateSnapshot(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("JSON 值不能为空");
        }
        try {
            MAPPER.readTree(value);
        } catch (JsonProcessingException ex) {
            // 不回显载荷或解析器片段，避免凭据等内容进入错误响应与日志。
            throw new IllegalArgumentException("JSON 必须为完整单根合法值");
        }
    }

    private static BizException invalid(String message) {
        return new BizException(AccessErrorCode.PERM_INVALID_PARAM.getCode(), message);
    }
}
