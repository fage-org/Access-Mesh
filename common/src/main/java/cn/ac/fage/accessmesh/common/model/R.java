package cn.ac.fage.accessmesh.common.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 统一API响应格式
 * <p>
 * 包含状态码、消息、数据、请求ID和追踪ID。
 * RResponseAdvice在响应序列化前自动填充requestId和traceId。
 * </p>
 *
 * @param <T> 数据类型
 */
@Getter
@Setter
public class R<T> {

    private int code;
    private String message;
    private T data;
    private String requestId;
    private String traceId;

    public R(int code, String message, T data, String requestId, String traceId) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.requestId = requestId;
        this.traceId = traceId;
    }

    public static <T> R<T> ok(T data) {
        return new R<>(200, "success", data, null, null);
    }

    public static <T> R<T> ok() {
        return ok(null);
    }

    public static <T> R<T> fail(int code, String message) {
        return new R<>(code, message, null, null, null);
    }

    public static <T> R<T> fail(int code, String message, String requestId) {
        return new R<>(code, message, null, requestId, null);
    }
}
