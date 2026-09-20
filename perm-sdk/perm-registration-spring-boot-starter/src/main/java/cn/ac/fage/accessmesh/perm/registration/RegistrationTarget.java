package cn.ac.fage.accessmesh.perm.registration;

/** 每次调用的租户/服务凭证；不把多租户凭证写入共享可变拦截器。 */
public record RegistrationTarget(Long tenantId, String serviceCode, String credentialId, String credentialSecret) {
    public RegistrationTarget {
        if (tenantId == null || tenantId <= 0 || blank(serviceCode) || blank(credentialId) || blank(credentialSecret)) {
            throw new IllegalArgumentException("registration requires tenantId, serviceCode and a complete service credential");
        }
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    @Override public String toString() { return "RegistrationTarget[tenantId=" + tenantId + ", serviceCode=" + serviceCode + ", credentials=REDACTED]"; }
}
