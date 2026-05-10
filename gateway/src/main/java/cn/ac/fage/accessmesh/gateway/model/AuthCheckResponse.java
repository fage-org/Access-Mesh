package cn.ac.fage.accessmesh.gateway.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 权限校验响应模型
 * <p>
 * 来自permission-center接口校验的响应数据。
 * 对应PermResult&lt;CheckInterfaceResp&gt;结构。
 * </p>
 */
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
     * 获取状态码
     *
     * @return 状态码
     */
    public int getCode() {
        return code;
    }

    /**
     * 设置状态码
     *
     * @param code 状态码
     */
    public void setCode(int code) {
        this.code = code;
    }

    /**
     * 获取结果消息
     *
     * @return 结果消息
     */
    public String getMessage() {
        return message;
    }

    /**
     * 设置结果消息
     *
     * @param message 结果消息
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * 获取校验结果数据
     *
     * @return 校验结果数据
     */
    public AuthCheckData getData() {
        return data;
    }

    /**
     * 设置校验结果数据
     *
     * @param data 校验结果数据
     */
    public void setData(AuthCheckData data) {
        this.data = data;
    }

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

        /**
         * 获取是否允许访问
         *
         * @return 是否允许访问
         */
        public Boolean getAllowed() {
            return allowed;
        }

        /**
         * 设置是否允许访问
         *
         * @param allowed 是否允许访问
         */
        public void setAllowed(Boolean allowed) {
            this.allowed = allowed;
        }

        /**
         * 获取匹配的角色ID
         *
         * @return 匹配的角色ID
         */
        public Long getMatchedRoleId() {
            return matchedRoleId;
        }

        /**
         * 设置匹配的角色ID
         *
         * @param matchedRoleId 匹配的角色ID
         */
        public void setMatchedRoleId(Long matchedRoleId) {
            this.matchedRoleId = matchedRoleId;
        }

        /**
         * 获取匹配的操作码
         *
         * @return 匹配的操作码
         */
        public String getMatchedOperationCode() {
            return matchedOperationCode;
        }

        /**
         * 设置匹配的操作码
         *
         * @param matchedOperationCode 匹配的操作码
         */
        public void setMatchedOperationCode(String matchedOperationCode) {
            this.matchedOperationCode = matchedOperationCode;
        }

        /**
         * 获取拒绝原因
         *
         * @return 拒绝原因
         */
        public String getDenyReason() {
            return denyReason;
        }

        /**
         * 设置拒绝原因
         *
         * @param denyReason 拒绝原因
         */
        public void setDenyReason(String denyReason) {
            this.denyReason = denyReason;
        }
    }
}