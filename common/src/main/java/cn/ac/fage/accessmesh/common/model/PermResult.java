package cn.ac.fage.accessmesh.common.model;

/**
 * 权限结果记录类
 * <p>
 * 统一的API响应格式，包含状态码、消息、数据、请求ID和追踪ID。
 * 用于所有API的响应数据传输。
 * </p>
 *
 * @param <T> 数据类型
 */
public record PermResult<T>(
    /**
     * 状态码（200=成功，其他=错误）
     */
    int code,

    /**
     * 结果消息
     */
    String message,

    /**
     * 返回数据
     */
    T data,

    /**
     * 请求ID（用于问题追踪）
     */
    String requestId,

    /**
     * 链路追踪ID（用于分布式追踪）
     */
    String traceId
) {
    /**
     * 创建成功结果（带数据）
     *
     * @param data 返回数据
     * @return 成功结果实例
     */
    public static <T> PermResult<T> success(T data) {
        return new PermResult<>(200, "success", data, null, null);
    }

    /**
     * 创建成功结果（无数据）
     *
     * @return 成功结果实例
     */
    public static <T> PermResult<T> success() {
        return success(null);
    }

    /**
     * 创建错误结果
     *
     * @param code    错误码
     * @param message 错误消息
     * @return 错误结果实例
     */
    public static <T> PermResult<T> error(int code, String message) {
        return new PermResult<>(code, message, null, null, null);
    }

    /**
     * 创建错误结果（带请求ID）
     *
     * @param code     错误码
     * @param message  错误消息
     * @param requestId 请求ID
     * @return 错误结果实例
     */
    public static <T> PermResult<T> error(int code, String message, String requestId) {
        return new PermResult<>(code, message, null, requestId, null);
    }
}