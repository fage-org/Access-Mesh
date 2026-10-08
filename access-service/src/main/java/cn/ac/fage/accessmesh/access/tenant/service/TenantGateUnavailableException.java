package cn.ac.fage.accessmesh.access.tenant.service;

/** 不能建立可信租户门禁状态，调用方必须拒绝，不使用旧快照放行。 */
public class TenantGateUnavailableException extends RuntimeException {
    public TenantGateUnavailableException() {
        super("租户暂不可用，请稍后重试");
    }

    public TenantGateUnavailableException(Throwable cause) {
        super("租户暂不可用，请稍后重试", cause);
    }
}
