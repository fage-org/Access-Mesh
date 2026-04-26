package cn.ac.fage.accessmesh.gateway.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Response from permission-center interface check.
 * Matches PermResult<CheckInterfaceResp> returned by permission-center.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthCheckResponse {

    private int code;
    private String message;
    private AuthCheckData data;

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public AuthCheckData getData() {
        return data;
    }

    public void setData(AuthCheckData data) {
        this.data = data;
    }

    public boolean isAllowed() {
        return data != null && Boolean.TRUE.equals(data.getAllowed());
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AuthCheckData {
        private Boolean allowed;
        private Long matchedRoleId;
        private String matchedOperationCode;
        private String denyReason;

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

        public String getDenyReason() {
            return denyReason;
        }

        public void setDenyReason(String denyReason) {
            this.denyReason = denyReason;
        }
    }
}
