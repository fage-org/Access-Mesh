package cn.ac.fage.accessmesh.gateway.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

/**
 * 权限校验响应模型
 * <p>
 * 来自permission-center接口校验的响应数据。
 * 对应PermResult&lt;CheckInterfaceResp&gt;结构。
 * </p>
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthCheckResponse {

    /**
     * 状态码
     */
    private int code;

    /**
     * 结果消息
     */
    private String message;

    /**
     * 校验结果数据
     */
    private AuthCheckData data;

    /**
     * 检查是否允许访问
     * <p>
     * 如果data不为null且allowed为true，则允许访问。
     * </p>
     *
     * @return 是否允许访问
     */
    public boolean isAllowed() {
        return data != null && Boolean.TRUE.equals(data.getAllowed());
    }

    /**
     * 权限校验数据
     * <p>
     * 包含是否允许、匹配的角色ID、匹配的操作码、拒绝原因等信息。
     * </p>
     */
    @Getter
    @Setter
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AuthCheckData {

        /**
         * 是否允许访问
         */
        private Boolean allowed;

        /**
         * 匹配的角色ID
         */
        private Long matchedRoleId;

        /**
         * 匹配的操作码
         */
        private String matchedOperationCode;

        /**
         * 拒绝原因
         */
        private String denyReason;
    }
}