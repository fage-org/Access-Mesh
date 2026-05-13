package cn.ac.fage.accessmesh.gateway.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

/**
 * 权限校验请求模型
 * <p>
 * 调用permission-center接口校验的请求参数。
 * 包含用户ID、服务编码、HTTP方法、路径、客户端IP等信息。
 * </p>
 */
@Getter
@Setter
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
     * 权限校验上下文
     * <p>
     * 包含IP地址和时间戳等上下文信息。
     * </p>
     */
    @Getter
    @Setter
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
    }
}