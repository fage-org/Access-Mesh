package cn.ac.fage.accessmesh.gateway.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Request model for calling permission-center interface check.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthCheckRequest {

    private Long userId;
    private String serviceCode;
    private String httpMethod;
    private String path;
    /** Flat field matching permission-center CheckInterfaceReq.clientIp */
    private String clientIp;
    private Context context;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getServiceCode() {
        return serviceCode;
    }

    public void setServiceCode(String serviceCode) {
        this.serviceCode = serviceCode;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public Context getContext() {
        return context;
    }

    public void setContext(Context context) {
        this.context = context;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Context {
        private String ip;
        private String timestamp;

        public String getIp() {
            return ip;
        }

        public void setIp(String ip) {
            this.ip = ip;
        }

        public String getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(String timestamp) {
            this.timestamp = timestamp;
        }
    }
}
