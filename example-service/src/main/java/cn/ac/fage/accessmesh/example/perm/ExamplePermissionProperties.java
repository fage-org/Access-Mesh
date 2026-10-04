package cn.ac.fage.accessmesh.example.perm;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.example.enums.ExampleErrorCode;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/** 示例应用的租户凭证配置；部署方负责租户与真实凭证绑定一致。 */
@ConfigurationProperties(prefix = "example.permission")
public record ExamplePermissionProperties(Map<String, Credential> tenantCredentials, Boolean allowInsecure) {
    public ExamplePermissionProperties {
        tenantCredentials = tenantCredentials == null ? Map.of() : Map.copyOf(tenantCredentials);
        if (!tenantCredentials.isEmpty() && allowInsecure == null) {
            throw new IllegalStateException("配置租户凭证必须显式声明 example.permission.allow-insecure");
        }
        for (String tenant : tenantCredentials.keySet()) {
            if (!tenant.matches("[1-9][0-9]*")) {
                throw new IllegalStateException("租户凭证映射的键必须为正租户 ID");
            }
        }
    }

    /** 没有对应租户的配置即拒绝，不使用 SDK 全局凭证兜底。 */
    public Credential require(String tenantId) {
        Credential credential = tenantId == null ? null : tenantCredentials.get(tenantId);
        if (credential == null) {
            throw new BizException(ExampleErrorCode.PERMISSION_DENIED.getCode(),
                ExampleErrorCode.PERMISSION_DENIED.getMessage());
        }
        return credential;
    }

    public record Credential(String credentialId, String credentialSecret) {
        public Credential {
            if (credentialId == null || credentialId.isBlank()
                || credentialSecret == null || credentialSecret.isBlank()) {
                throw new IllegalStateException("租户凭证的 credential-id 与 credential-secret 必须成对非空");
            }
        }

        @Override
        public String toString() {
            return "Credential[credentialId=" + credentialId + ", credentialSecret=***]";
        }
    }
}
