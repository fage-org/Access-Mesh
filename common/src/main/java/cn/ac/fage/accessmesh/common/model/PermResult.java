package cn.ac.fage.accessmesh.common.model;

public record PermResult<T>(
    int code,
    String message,
    T data,
    String requestId,
    String traceId
) {
    public static <T> PermResult<T> success(T data) {
        return new PermResult<>(200, "success", data, null, null);
    }

    public static <T> PermResult<T> success() {
        return success(null);
    }

    public static <T> PermResult<T> error(int code, String message) {
        return new PermResult<>(code, message, null, null, null);
    }

    public static <T> PermResult<T> error(int code, String message, String requestId) {
        return new PermResult<>(code, message, null, requestId, null);
    }
}
