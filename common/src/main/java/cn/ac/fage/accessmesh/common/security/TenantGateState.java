package cn.ac.fage.accessmesh.common.security;

/** 租户生命周期门禁；未知状态不得授予访问。 */
public record TenantGateState(Status status, long sessionEpoch) {
    public enum Status { ENABLED, DISABLED, UNAVAILABLE }

    public TenantGateState {
        java.util.Objects.requireNonNull(status, "status");
        if (status == Status.UNAVAILABLE ? sessionEpoch != 0 : sessionEpoch <= 0) {
            throw new IllegalArgumentException("invalid tenant gate epoch");
        }
    }

    public static TenantGateState fromWire(String wire) {
        if (wire != null) {
            String[] parts = wire.split("\\|", -1);
            if (parts.length == 2 && ("ENABLED".equals(parts[0]) || "DISABLED".equals(parts[0]))
                && parts[1].matches("[1-9][0-9]*")) {
                try {
                    return new TenantGateState(Status.valueOf(parts[0]), Long.parseLong(parts[1]));
                } catch (NumberFormatException ignored) {
                    // 溢出的代次不是可信门禁状态。
                }
            }
        }
        return unavailable();
    }

    public static TenantGateState unavailable() {
        return new TenantGateState(Status.UNAVAILABLE, 0);
    }

    public boolean permitsService() {
        return status == Status.ENABLED;
    }

    public boolean permitsSession(long expectedEpoch) {
        return permitsService() && sessionEpoch == expectedEpoch;
    }
}
