package cn.ac.fage.accessmesh.gateway.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 权限校验请求模型
 * <p>
 * 调用permission-center接口校验的请求参数。
 * 包含用户ID、服务编码、HTTP方法、路径、客户端IP等信息。
 * </p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthCheckRequest {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 服务编码
     */
    private String serviceCode;

    /**
     * HTTP方法
     */
    private String httpMethod;

    /**
     * 请求路径
     */
    private String path;

    /**
     * 客户端IP地址
     * <p>
     * 平铺字段，对应permission-center CheckInterfaceReq.clientIp
     * </p>
     */
    private String clientIp;

    /**
     * 上下文信息
     */
    private Context context;

    /**
     * 获取用户ID
     *
     * @return 用户ID
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * 设置用户ID
     *
     * @param userId 用户ID
     */
    public void setUserId(Long userId) {
        this.userId = userId;
    }

    /**
     * 获取服务编码
     *
     * @return 服务编码
     */
    public String getServiceCode() {
        return serviceCode;
    }

    /**
     * 设置服务编码
     *
     * @param serviceCode 服务编码
     */
    public void setServiceCode(String serviceCode) {
        this.serviceCode = serviceCode;
    }

    /**
     * 获取HTTP方法
     *
     * @return HTTP方法
     */
    public String getHttpMethod() {
        return httpMethod;
    }

    /**
     * 设置HTTP方法
     *
     * @param httpMethod HTTP方法
     */
    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    /**
     * 获取请求路径
     *
     * @return 请求路径
     */
    public String getPath() {
        return path;
    }

    /**
     * 设置请求路径
     *
     * @param path 请求路径
     */
    public void setPath(String path) {
        this.path = path;
    }

    /**
     * 获取客户端IP地址
     *
     * @return 客户端IP地址
     */
    public String getClientIp() {
        return clientIp;
    }

    /**
     * 设置客户端IP地址
     *
     * @param clientIp 客户端IP地址
     */
    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    /**
     * 获取上下文信息
     *
     * @return 上下文信息
     */
    public Context getContext() {
        return context;
    }

    /**
     * 设置上下文信息
     *
     * @param context 上下文信息
     */
    public void setContext(Context context) {
        this.context = context;
    }

    /**
     * 权限校验上下文
     * <p>
     * 包含IP地址和时间戳等上下文信息。
     * </p>
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Context {

        /**
         * IP地址
         */
        private String ip;

        /**
         * 时间戳
         */
        private String timestamp;

        /**
         * 获取IP地址
         *
         * @return IP地址
         */
        public String getIp() {
            return ip;
        }

        /**
         * 设置IP地址
         *
         * @param ip IP地址
         */
        public void setIp(String ip) {
            this.ip = ip;
        }

        /**
         * 获取时间戳
         *
         * @return 时间戳
         */
        public String getTimestamp() {
            return timestamp;
        }

        /**
         * 设置时间戳
         *
         * @param timestamp 时间戳
         */
        public void setTimestamp(String timestamp) {
            this.timestamp = timestamp;
        }
    }
}