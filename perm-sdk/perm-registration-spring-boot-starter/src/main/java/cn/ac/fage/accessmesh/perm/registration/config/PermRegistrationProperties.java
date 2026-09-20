package cn.ac.fage.accessmesh.perm.registration.config;

import cn.ac.fage.accessmesh.perm.registration.RegistrationTarget;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 复用 perm 凭证键；可选发布开关和静态文件位置只属于 registration。 */
@Getter
@Setter
@ConfigurationProperties("perm")
public class PermRegistrationProperties {
    private Long tenantId;
    private String serviceCode;
    private String credentialId;
    private String credentialSecret;
    private String allowInsecure;
    private Registration registration = new Registration();

    public RegistrationTarget defaultTarget() { return new RegistrationTarget(tenantId,serviceCode,credentialId,credentialSecret); }
    public Boolean declaredAllowInsecure() {
        if (allowInsecure == null || !("true".equalsIgnoreCase(allowInsecure.trim()) || "false".equalsIgnoreCase(allowInsecure.trim()))) {
            throw new IllegalStateException("perm.allow-insecure must be explicitly true or false");
        }
        return Boolean.valueOf(allowInsecure.trim());
    }
    public void validateCredentialPair() {
        if ((credentialId == null) != (credentialSecret == null)
                || credentialId != null && (credentialId.isBlank() || credentialSecret.isBlank())) {
            throw new IllegalStateException("perm.credential-id and perm.credential-secret must be configured together");
        }
    }
    @Getter
    @Setter
    public static class Registration {
        private boolean enabled;
        private String manifestLocation;
    }
}
