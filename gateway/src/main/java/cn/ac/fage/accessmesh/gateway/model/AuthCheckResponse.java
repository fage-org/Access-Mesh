package cn.ac.fage.accessmesh.gateway.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Response from permission-center interface check.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthCheckResponse {

    private int code;
    private AuthData data;

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public AuthData getData() {
        return data;
    }

    public void setData(AuthData data) {
        this.data = data;
    }

    public boolean isAllowed() {
        return data != null && Boolean.TRUE.equals(data.allowed);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AuthData {
        private Boolean allowed;
        private Long matchedRoleId;
        private String matchedOperationCode;
        private String reason;

        public Boolean getAllowed() {
            return allowed;
        }

        public void setAllowed(Boolean allowed) {
            this.allowed = allowed;
        }

        public Long getMatchedRoleId() {
            return matchedRoleId;
        }

        public void setMatchedRoleId(Long matchedRoleId) {
            this.matchedRoleId = matchedRoleId;
        }

        public String getMatchedOperationCode() {
            return matchedOperationCode;
        }

        public void setMatchedOperationCode(String matchedOperationCode) {
            this.matchedOperationCode = matchedOperationCode;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }
}
