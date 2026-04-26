package cn.ac.fage.accessmesh.gateway.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Gateway unified response body.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GatewayResponse {

    private final int code;
    private final String message;
    private final Object data;
    private String requestId;
    private String traceId;

    private GatewayResponse(int code, String message, Object data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static GatewayResponse success(Object data) {
        return new GatewayResponse(200, "success", data);
    }

    public static GatewayResponse error(int code, String message) {
        return new GatewayResponse(code, message, null);
    }

    public static GatewayResponse error(int code, String message, Object data) {
        return new GatewayResponse(code, message, data);
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public Object getData() {
        return data;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }
}
