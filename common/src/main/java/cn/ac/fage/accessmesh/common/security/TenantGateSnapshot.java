package cn.ac.fage.accessmesh.common.security;

/** 一次 Lua 原子读取的门禁与 Redis 进程身份，供原生会话校验使用。 */
public record TenantGateSnapshot(TenantGateState state, String redisProcessId) {
    public static TenantGateSnapshot fromWire(String wire) {
        if (wire != null) {
            int separator = wire.indexOf('|');
            if (separator > 0) {
                String process = wire.substring(0, separator);
                if (process.matches("[a-f0-9]{40}")) {
                    return new TenantGateSnapshot(TenantGateState.fromWire(wire.substring(separator + 1)), process);
                }
            }
        }
        return new TenantGateSnapshot(TenantGateState.unavailable(), null);
    }
}
