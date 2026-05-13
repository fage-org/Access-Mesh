package cn.ac.fage.accessmesh.gateway.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

/**
 * 网关统一响应体
 * <p>
 * 网关返回给客户端的统一响应格式。
 * 包含状态码、消息、数据、请求ID和追踪ID。
 * </p>
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GatewayResponse {

    /**
     * 状态码
     */
    private final int code;

    /**
     * 结果消息
     */
    private final String message;

    /**
     * 返回数据
     */
    private final Object data;

    /**
     * 请求ID
     */
    @Setter
    private String requestId;

    /**
     * 链路追踪ID
     */
    @Setter
    private String traceId;

    /**
     * 构造网关响应
     *
     * @param code    状态码
     * @param message 结果消息
     * @param data    返回数据
     */
    private GatewayResponse(int code, String message, Object data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * 创建成功响应
     *
     * @param data 返回数据
     * @return 成功响应实例
     */
    public static GatewayResponse success(Object data) {
        return new GatewayResponse(200, "success", data);
    }

    /**
     * 创建错误响应
     *
     * @param code    错误码
     * @param message 错误消息
     * @return 错误响应实例
     */
    public static GatewayResponse error(int code, String message) {
        return new GatewayResponse(code, message, null);
    }

    /**
     * 创建错误响应（带数据）
     *
     * @param code    错误码
     * @param message 错误消息
     * @param data    返回数据
     * @return 错误响应实例
     */
    public static GatewayResponse error(int code, String message, Object data) {
        return new GatewayResponse(code, message, data);
    }
}